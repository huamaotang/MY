package com.example.crm.android;
import org.json.JSONObject;
import java.io.Serializable;
public class FinanceNews implements Serializable {
    public int id; public String newsId; public int categoryTag; public String categoryName; public String content; public String createTime; public String docUrl; public String tagsJson;
    public static FinanceNews fromJson(JSONObject json) { FinanceNews n=new FinanceNews(); n.id=json.optInt("id"); n.newsId=Fund.optionalString(json,"newsId"); n.categoryTag=json.optInt("categoryTag"); n.categoryName=Fund.optionalString(json,"categoryName"); n.content=Fund.optionalString(json,"content"); n.createTime=Fund.optionalString(json,"createTime"); n.docUrl=Fund.optionalString(json,"docUrl"); n.tagsJson=Fund.optionalString(json,"tagsJson"); return n; }
}
