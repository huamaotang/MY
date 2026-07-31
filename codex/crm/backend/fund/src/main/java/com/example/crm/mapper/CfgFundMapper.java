package com.example.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.crm.entity.CfgFund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Mapper
public interface CfgFundMapper extends BaseMapper<CfgFund> {
    @Select("<script>SELECT f.*,CASE WHEN uf.id IS NULL THEN 0 ELSE 1 END AS favorite FROM fund_detail f "
            + "LEFT JOIN (SELECT p1.* FROM fund_performance_history p1 JOIN (SELECT fund_code,MAX(nav_date) nav_date FROM fund_performance_history GROUP BY fund_code) pm ON pm.fund_code=p1.fund_code AND pm.nav_date=p1.nav_date) p ON p.fund_code=f.fund_code "
            + "LEFT JOIN (SELECT r1.* FROM fund_rating r1 JOIN (SELECT fund_code,MAX(rating_date) rating_date FROM fund_rating GROUP BY fund_code) rm ON rm.fund_code=r1.fund_code AND rm.rating_date=r1.rating_date) r ON r.fund_code=f.fund_code "
            + "LEFT JOIN fund_score_profile sp ON sp.is_active=1 "
            + "LEFT JOIN (SELECT sr1.* FROM fund_score_result sr1 JOIN (SELECT profile_id,fund_code,MAX(as_of_date) as_of_date FROM fund_score_result GROUP BY profile_id,fund_code) srm ON srm.profile_id=sr1.profile_id AND srm.fund_code=sr1.fund_code AND srm.as_of_date=sr1.as_of_date) sr ON sr.profile_id=sp.id AND sr.fund_code=f.fund_code "
            + "LEFT JOIN user_fund_favorite uf ON uf.fund_code=f.fund_code AND uf.owner_username=#{ownerUsername} "
            + "WHERE 1=1 "
            + "<if test='keyword != null and keyword != \"\"'>AND (f.fund_name LIKE CONCAT('%',#{keyword},'%') OR f.fund_code LIKE CONCAT('%',#{keyword},'%') OR f.fund_manager LIKE CONCAT('%',#{keyword},'%')) </if>"
            + "<if test='fundType != null and fundType != \"\"'>AND f.fund_type=#{fundType} </if>"
            + "<if test='canBuy != null'>AND f.can_buy=#{canBuy} </if>"
            + "<if test='favoritesOnly'>AND uf.id IS NOT NULL </if>"
            + "ORDER BY ${sortExpression} ${sortDirection}, f.fund_code ASC</script>")
    Page<CfgFund> selectFundPage(Page<CfgFund> page,
                                 @Param("ownerUsername") String ownerUsername,
                                 @Param("keyword") String keyword,
                                 @Param("fundType") String fundType,
                                 @Param("canBuy") Boolean canBuy,
                                 @Param("favoritesOnly") boolean favoritesOnly,
                                 @Param("sortExpression") String sortExpression,
                                 @Param("sortDirection") String sortDirection);
}
