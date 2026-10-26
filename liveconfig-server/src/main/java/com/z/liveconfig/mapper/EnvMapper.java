package com.z.liveconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.z.liveconfig.entity.Env;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EnvMapper extends BaseMapper<Env> {

    /**
     * 配置版本 +1 并返回新值。
     * 同一事务里先自增再取，读到的一定是自己写下的那个版本，不会被并发请求拿重。
     */
    @Update("UPDATE env SET config_version = config_version + 1 WHERE id = #{envId}")
    int bumpConfigVersion(@Param("envId") Long envId);
}
