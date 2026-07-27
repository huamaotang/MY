package com.example.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.crm.entity.CfgFund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Mapper
public interface CfgFundMapper extends BaseMapper<CfgFund> {
    @Select("<script>SELECT f.* FROM fund_detail f "
            + "LEFT JOIN (SELECT p1.* FROM fund_performance_history p1 JOIN (SELECT fund_code,MAX(nav_date) nav_date FROM fund_performance_history GROUP BY fund_code) pm ON pm.fund_code=p1.fund_code AND pm.nav_date=p1.nav_date) p ON p.fund_code=f.fund_code "
            + "LEFT JOIN (SELECT r1.* FROM fund_rating r1 JOIN (SELECT fund_code,MAX(rating_date) rating_date FROM fund_rating GROUP BY fund_code) rm ON rm.fund_code=r1.fund_code AND rm.rating_date=r1.rating_date) r ON r.fund_code=f.fund_code "
            + "WHERE 1=1 "
            + "<if test='keyword != null and keyword != \"\"'>AND (f.fund_name LIKE CONCAT('%',#{keyword},'%') OR f.fund_code LIKE CONCAT('%',#{keyword},'%') OR f.fund_manager LIKE CONCAT('%',#{keyword},'%')) </if>"
            + "<if test='fundType != null and fundType != \"\"'>AND f.fund_type=#{fundType} </if>"
            + "ORDER BY ${sortExpression} ${sortDirection}, f.fund_code ASC</script>")
    Page<CfgFund> selectFundPage(Page<CfgFund> page,
                                 @Param("keyword") String keyword,
                                 @Param("fundType") String fundType,
                                 @Param("sortExpression") String sortExpression,
                                 @Param("sortDirection") String sortDirection);
}
