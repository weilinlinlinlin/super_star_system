package com.xuecheng.content.service.impl;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.CommonError;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.config.MultipartSupportConfig;
import com.xuecheng.content.feignclient.MediaServiceClient;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.mapper.CoursePublishMapper;
import com.xuecheng.content.mapper.CoursePublishPreMapper;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.model.po.CoursePublishPre;
import com.xuecheng.content.service.CourseBaseInfoService;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.content.service.TeachplanService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Mr.M
 * @version 1.0
 * @description 课程发布相关接口实现
 * @date 2023/2/21 10:04
 */
@Slf4j
@Service
public class CoursePublishServiceImpl implements CoursePublishService {

    @Autowired
    CourseBaseInfoService courseBaseInfoService;

    @Autowired
    TeachplanService teachplanService;

    @Autowired
    CourseBaseMapper courseBaseMapper;

    @Autowired
    CourseMarketMapper courseMarketMapper;

    @Autowired
    CoursePublishPreMapper coursePublishPreMapper;
    @Autowired
    CoursePublishMapper coursePublishMapper;

    @Autowired
    MqMessageService mqMessageService;
    @Autowired
    MediaServiceClient mediaServiceClient;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public CoursePreviewDto getCoursePreviewInfo(Long courseId) {
        CoursePreviewDto coursePreviewDto = new CoursePreviewDto();
        //课程基本信息,营销信息
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);
        coursePreviewDto.setCourseBase(courseBaseInfo);
        //课程计划信息
        List<TeachplanDto> teachplanTree = teachplanService.findTeachplanTree(courseId);
        coursePreviewDto.setTeachplans(teachplanTree);

        return coursePreviewDto;
    }

    @Transactional
    @Override
    public void commitAudit(Long companyId, Long courseId) {

        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);
        if (courseBaseInfo == null) {
            XueChengPlusException.cast("课程找不到");
        }
        //审核状态
        String auditStatus = courseBaseInfo.getAuditStatus();

        //如果课程的审核状态为已提交则不允许提交
        if (auditStatus.equals("202003")) {
            XueChengPlusException.cast("课程已提交请等待审核");
        }
        //本机构只能提交本机构的课程
        //todo:本机构只能提交本机构的课程

        //课程的图片、计划信息没有填写也不允许提交
        String pic = courseBaseInfo.getPic();
        if (StringUtils.isEmpty(pic)) {
            XueChengPlusException.cast("请求上传课程图片");
        }
        //查询课程计划
        //课程计划信息
        List<TeachplanDto> teachplanTree = teachplanService.findTeachplanTree(courseId);
        if (teachplanTree == null || teachplanTree.size() == 0) {
            XueChengPlusException.cast("请编写课程计划");
        }

        //查询到课程基本信息、营销信息、计划等信息插入到课程预发布表
        CoursePublishPre coursePublishPre = new CoursePublishPre();
        BeanUtils.copyProperties(courseBaseInfo, coursePublishPre);
        //设置机构id
        coursePublishPre.setCompanyId(companyId);
        //营销信息
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);
        //转json
        String courseMarketJson = JSON.toJSONString(courseMarket);
        coursePublishPre.setMarket(courseMarketJson);
        //计划信息
        //转json
        String teachplanTreeJson = JSON.toJSONString(teachplanTree);
        coursePublishPre.setTeachplan(teachplanTreeJson);
        //状态为已提交
        coursePublishPre.setStatus("202003");
        //提交时间
        coursePublishPre.setCreateDate(LocalDateTime.now());
        //查询预发布表，如果有记录则更新，没有则插入
        CoursePublishPre coursePublishPreObj = coursePublishPreMapper.selectById(courseId);
        if (coursePublishPreObj == null) {
            //插入
            coursePublishPreMapper.insert(coursePublishPre);
        } else {
            //更新
            coursePublishPreMapper.updateById(coursePublishPre);
        }

        //更新课程基本信息表的审核状态为已提交
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        courseBase.setAuditStatus("202003");//审核状态为已提交

        courseBaseMapper.updateById(courseBase);
    }

    @Transactional
    @Override
    public void publish(Long companyId, Long courseId) {

        //查询预发布表
        CoursePublishPre coursePublishPre = coursePublishPreMapper.selectById(courseId);
        if (coursePublishPre == null) {
            XueChengPlusException.cast("课程没有审核记录，无法发布");
        }
        //状态
        String status = coursePublishPre.getStatus();
        //课程如果没有审核通过不允许发布
        if (!status.equals("202004")) {
            XueChengPlusException.cast("课程没有审核通过不允许发布");
        }

        //向课程发布表写入数据
        CoursePublish coursePublish = new CoursePublish();
        BeanUtils.copyProperties(coursePublishPre, coursePublish);
        //先查询课程发布，如果有则更新，没有再添加
        CoursePublish coursePublishObj = coursePublishMapper.selectById(courseId);
        if (coursePublishObj == null) {
            coursePublishMapper.insert(coursePublish);
        } else {
            coursePublishMapper.updateById(coursePublish);
        }

        //向消息表写入数据
//        mqMessageService.addMessage("course_publish",String.valueOf(courseId),null,null);
        saveCoursePublishMessage(courseId);

        //将预发布表数据删除
        coursePublishPreMapper.deleteById(courseId);

    }

    @Override
    public File generateCourseHtml(Long courseId) {

        Configuration configuration = new Configuration(Configuration.getVersion());
        //最终的静态文件
        File htmlFile = null;
        try {
            //拿到classpath路径
//            String classpath = this.getClass().getResource("/").getPath();
//            //指定模板的目录
//            configuration.setDirectoryForTemplateLoading(new File(classpath+"/templates/"));
            //更改为如下方式
            configuration.setTemplateLoader(new ClassTemplateLoader(this.getClass().getClassLoader(), "/templates"));
            //指定编码
            configuration.setDefaultEncoding("utf-8");

            //得到模板
            Template template = configuration.getTemplate("course_template.ftl");
            //准备数据
            CoursePreviewDto coursePreviewInfo = this.getCoursePreviewInfo(courseId);
            HashMap<String, Object> map = new HashMap<>();
            map.put("model", coursePreviewInfo);

            //Template template 模板, Object model 数据
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);
            //输入流
            InputStream inputStream = IOUtils.toInputStream(html, "utf-8");
            htmlFile = File.createTempFile("coursepublish", ".html");
            //输出文件
            FileOutputStream outputStream = new FileOutputStream(htmlFile);
            //使用流将html写入文件
            IOUtils.copy(inputStream, outputStream);
        } catch (Exception ex) {
            log.error("页面静态化出现问题,课程id:{}", courseId, ex);
            ex.printStackTrace();
        }

        return htmlFile;
    }

    @Override
    public void uploadCourseHtml(Long courseId, File file) {
        try {
            //将file转成MultipartFile
            MultipartFile multipartFile = MultipartSupportConfig.getMultipartFile(file);
            //远程调用得到返回值
            String upload = mediaServiceClient.upload(multipartFile, "course/" + courseId + ".html");
            if (upload == null) {
                log.debug("远程调用走降级逻辑得到上传的结果为null,课程id:{}", courseId);
                XueChengPlusException.cast("上传静态文件过程中存在异常");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            XueChengPlusException.cast("上传静态文件过程中存在异常");
        }

    }

    /**
     * @param courseId 课程id
     * @return void
     * @description 保存消息表记录
     * @author Mr.M
     * @date 2022/9/20 16:32
     */
    private void saveCoursePublishMessage(Long courseId) {

        MqMessage mqMessage = mqMessageService.addMessage("course_publish", String.valueOf(courseId), null, null);
        if (mqMessage == null) {
            XueChengPlusException.cast(CommonError.UNKOWN_ERROR);
        }

    }

    /**
     * 根据课程 id查询课程发布信息
     *
     * @param courseId
     * @return
     */
    public CoursePublish getCoursePublish(Long courseId) {
        CoursePublish coursePublish = coursePublishMapper.selectById(courseId);
        return coursePublish;
    }

    //解决缓存穿透
//    @Override
//    public CoursePublish getCoursePublishCache(Long courseId) {
//
//
//
//        //从缓存中查询
//        Object jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
//        //缓存中有
//        if(jsonObj!=null){
////            System.out.println("=============从缓存中查询=============");
//            //缓存中有直接返回数据
//            String jsonString = jsonObj.toString();
//            if("null".equals(jsonString)){
//                return null;
//            }
//            CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
//            return coursePublish;
//        }else{
//            System.out.println("==查询数据库==");
//            //从数据库查询
//            CoursePublish coursePublish = getCoursePublish(courseId);
////            if(coursePublish!=null){
//                //查询完成再存储到redis
//                redisTemplate.opsForValue().set("course:"+courseId,JSON.toJSONString(coursePublish),30, TimeUnit.SECONDS);
////            }
//            return coursePublish;
//        }
//    }

    //使用同步锁解决缓存击穿
//    @Override
//    public CoursePublish getCoursePublishCache(Long courseId) {
//
//
//            //从缓存中查询
//            Object jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
//            //缓存中有
//            if(jsonObj!=null){
////            System.out.println("=============从缓存中查询=============");
//                //缓存中有直接返回数据
//                String jsonString = jsonObj.toString();
//                if("null".equals(jsonString)){
//                    return null;
//                }
//                CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
//                return coursePublish;
//            }else{
//                synchronized (this){
//                    //再次查询一下缓存
//                    //从缓存中查询
//                    jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
//                    //缓存中有
//                    if(jsonObj!=null) {
//                        //缓存中有直接返回数据
//                        String jsonString = jsonObj.toString();
//                        if("null".equals(jsonString)){
//                            return null;
//                        }
//                        CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
//                        return coursePublish;
//                    }
//                    System.out.println("==查询数据库==");
//                    //从数据库查询
//                    CoursePublish coursePublish = getCoursePublish(courseId);
//                    //查询完成再存储到redis
//                    redisTemplate.opsForValue().set("course:"+courseId,JSON.toJSONString(coursePublish),300, TimeUnit.SECONDS);
//                    return coursePublish;
//
//                }
//
//
//
//            }

    //使用redisson实现分布式锁
    @Override
    public CoursePublish getCoursePublishCache(Long courseId) {


        //从缓存中查询
        Object jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
        //缓存中有
        if (jsonObj != null) {
//            System.out.println("=============从缓存中查询=============");
            //缓存中有直接返回数据
            String jsonString = jsonObj.toString();
            if ("null".equals(jsonString)) {
                return null;
            }
            CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
            return coursePublish;
        } else {
            RLock lock = redissonClient.getLock("coursequerylock:" + courseId);
            //获取分布式锁
            lock.lock();
            try {
                //再次查询一下缓存
                //从缓存中查询
                jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
                //缓存中有
                if (jsonObj != null) {
                    //缓存中有直接返回数据
                    String jsonString = jsonObj.toString();
                    if ("null".equals(jsonString)) {
                        return null;
                    }
                    CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
                    return coursePublish;
                }
                System.out.println("==查询数据库==");
//                try {
//                    //手动延迟，测试锁的续期功能
//                    Thread.sleep(60000);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
                //从数据库查询
                CoursePublish coursePublish = getCoursePublish(courseId);
                //查询完成再存储到redis
                redisTemplate.opsForValue().set("course:" + courseId, JSON.toJSONString(coursePublish), 300, TimeUnit.SECONDS);
                return coursePublish;

            } finally {
                //手动释放锁
                lock.unlock();
            }
        }


    }

}
