package com.xuecheng.content.model.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 * @date 2023/2/14 10:22
 */
@Data
public class EditCourseDto extends AddCourseDto {

 @ApiModelProperty(value = "课程id", required = true)
 private Long id;

}
