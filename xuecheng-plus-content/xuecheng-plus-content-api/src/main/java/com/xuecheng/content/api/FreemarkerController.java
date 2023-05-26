package com.xuecheng.content.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Mr.M
 * @version 1.0
 * @description Freemarker入门程序
 * @date 2023/2/19 16:12
 */
@Controller
public class FreemarkerController {

    @GetMapping("/testfreemarker")
    public ModelAndView test() {

        ModelAndView modelAndView = new ModelAndView();
        //指定模型
        modelAndView.addObject("name","小明");
        //指定模板
        modelAndView.setViewName("test");//根据视图名称加.ftl找到模板
        return modelAndView;


    }
}
