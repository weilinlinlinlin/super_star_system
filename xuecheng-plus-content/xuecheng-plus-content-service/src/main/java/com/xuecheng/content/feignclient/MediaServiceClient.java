package com.xuecheng.content.feignclient;

import com.xuecheng.content.config.MultipartSupportConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @author Mr.M
 * @version 1.0
 * @description 远程调用媒资服务的接口
 * @date 2023/2/22 10:26
 */
//使用fallback定义降级类是无法拿到熔断异常,使用FallbackFactory可以拿到熔断的异常信息
@FeignClient(value = "media-api",configuration = {MultipartSupportConfig.class},fallbackFactory = MediaServiceClientFallbackFactory.class)
public interface MediaServiceClient {

    @RequestMapping(value = "/media/upload/coursefile",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(@RequestPart("filedata") MultipartFile filedata,
                                      @RequestParam(value= "objectName",required=false) String objectName) throws IOException;

}
