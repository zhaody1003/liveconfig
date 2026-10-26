package com.z.liveconfig.mapper;

import org.apache.ibatis.annotations.Select;

public interface PingMapper {

    @Select("SELECT VERSION()")
    String dbVersion();
}
