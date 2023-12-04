package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JS1_PXDEFENSENEWS
 * @author 김승욱
 */
public class DefenseNewsExtension implements Extension, AddonExtension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private String cl_cd;
    private String origin_cd;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private boolean error_exist;
    private StringBuffer tagList;
    private boolean isTest;

    private String url;

    Map<String, String> imgHeader;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== DefaultExtension Start ===");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
//        BbsSetting setting = Configuration.getInstance().getBbsSetting(dqPageInfo.getBbsId());
//        RobotConf robotConf = Configuration.getInstance().getRobotConf();
//        BbsPage pageList = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 0, "https://www.fnn.jp/category/news/%E5%85%A8%E5%9B%BD?page=1");
//        BbsPage pageContent = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 1, "*");
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(reposit);

        imgHeader = new HashMap<>();

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        imgHeader.put("User-Agent", RandomUserAgentGenerator.generateRandomUserAgent());
        imgHeader.put("Cache-Control", "max-age=0");
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {

        //https://www.defensenews.com/pf/api/v3/content/fetch/story-feed-sections?query=%7B%22excludeSections%22%3A%22%2Fvideo%2C%2Fvideos%22%2C%22feedOffset%22%3A0%2C%22feedSize%22%3A10%2C%22includeSections%22%3A%22%2Fair%22%7D&filter=%7B_id%2Ccontent_elements%7B_id%2Cadditional_properties%7Badvertising%7BcommercialAdNode%2CplayAds%2CplayVideoAds%2CvideoAdZone%7D%7D%2Ccanonical_url%7D%7D&d=115&_website=defense-news&page=0

        if(url.contains("&page=")){
            String page = extractPage(url,1);
            String category=extractPage(url,2);
            url="https://www.defensenews.com/pf/api/v3/content/fetch/story-feed-sections?query=%7B%22excludeSections%22%3A%22%2Fvideo%2C%2Fvideos%22%2C%22feedOffset%22%3A"+page+"%2C%22feedSize%22%3A10%2C%22includeSections%22%3A%22%2F"+category+"%22%7D&filter=%7B_id%2Ccontent_elements%7B_id%2Cadditional_properties%7Badvertising%7BcommercialAdNode%2CplayAds%2CplayVideoAds%2CvideoAdZone%7D%7D%2Ccanonical_url%7D%7D&d=115&_website=defense-news";
        }

        this.url=url;
        return url;
    }

    private String extractPage(String url,int i) {
        // 정규표현식 패턴
        //String pattern = "feedOffset%22%3A(\\d+)";
        String pattern;
        if(i==1) pattern = "page=(\\d+)";
        else pattern = "includeSections%22%3A%22%2F([^%]+)%22";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(url);

        if (m.find()) {
            // 찾은 값 반환
            return m.group(1);
        } else {
            // 찾지 못한 경우에 대한 예외처리 또는 기본값 설정
            return "page 값을 찾을 수 없습니다.";
        }
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        commonUtil=new CommonUtil();
        tagList.delete(0,tagList.length());
        Elements urlList=null;
        JSONParser parser = new JSONParser();
        if (dqPageInfo.getParentUrl() == null){
            Document document = Jsoup.parse(htmlSrc);
            String domain= commonUtil.getDomain(url);
            urlList = document.select("body");
            JSONObject jsonObject = null;
            try {
                jsonObject = (JSONObject) parser.parse(urlList.text());
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            JSONArray DataJsonArray= (JSONArray) jsonObject.get("content_elements");
            tagList.append("<!--List Start-->");
            for (Object item : DataJsonArray){
                JSONObject urlCode= (JSONObject) item;
                String urlSrc= (String) urlCode.get("canonical_url");
                if (tagList.indexOf(String.valueOf(urlSrc)) == -1) {
                    tagList.append("\n<a href =\"" + domain+urlSrc+"\"></a>");
                }
            }
            tagList.append("\n<!--List End-->");
            htmlSrc=tagList.toString();
//            System.out.println("htmlSrc = " + htmlSrc);
        } else {
            /* 변수선언 start */
            String title="";
            String content = "";
            String datetime = "";
            String datetimeHtml="";
            /* 변수선언 end */

            /* 파싱 start */
            Document document = Jsoup.parse(htmlSrc);

            //System.out.println("document = " + document);
            title=document.getElementsByTag("title").text();
//            System.out.println("title = " + title);

            //create date
            datetimeHtml=document.getElementsByTag("time").attr("datetime");

            // 문자열을 Instant 객체로 변환
            Instant instant = Instant.parse(datetimeHtml);

            // Instant을 LocalDateTime으로 변환
            LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"));

            // DateTimeFormatter를 사용하여 포맷 지정
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            // LocalDateTime을 원하는 형식으로 포맷
            datetime = localDateTime.format(formatter);

//            System.out.println("datetime = " + datetime);

            // content
            Elements contentHtml = document.select("article[class=default__ArticleBody-sc-1mncpzl-2 jNAvLn  o-articleBody c-articleBody articleBody --featured]");
            contentHtml.select("div").remove();
            content=contentHtml.toString();
//            System.out.println("contentHtml = " + contentHtml);
            /* 파싱 end */

            /* 태그 생성 start */
            htmlSrc = commonUtil.makeCollectContext(title, datetime, content);
//            System.out.println("htmlSrc = " + htmlSrc);
            /* 태그 생성 end */
        }
        return htmlSrc;
    }

    @Override
    public List<String> makeNewUrls(String maviUrl, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
        doc_id++;
        for (int i = 0; i < row.size(); i++) {
            String nodeId = row.getNodeByIdx(i).getId();
            String nodeName = row.getNodeByIdx(i).getName();
            String nodeValue = row.getNodeByIdx(i).getValue();
            if (nodeName.equals("source_class")) {
                cl_cd = nodeValue;
            } else if (nodeName.equals("source_ID")) {
                origin_cd = nodeValue;
            } else if (nodeName.equals("document_id")) {
                row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
            }
        }
    }

    @Override
    public boolean validData(Row row, DqPageInfo dqPageInfo) {
        boolean isCheck = true;
        String title = "";
        String content = "";
        String documentId = String.format("%06d", doc_id);
        try {
            for (int i = 0; i < row.size(); i++) {
                String nodeName = row.getNodeByIdx(i).getName();
                String nodeValue = row.getNodeByIdx(i).getValue();
                if (nodeName.equals("title")) {
                    title = nodeValue;
                } else if (nodeName.equals("content")) {
                    content = nodeValue;
                }
            }
            if (title.equals("") || content.equals("")) {
                isCheck = false;
                connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
            e.printStackTrace();
        }

        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
            if (!connectionUtil.isLocal() && !isTest) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== DefaultExtension End ===");
        }
    }


    @Override
    public Map<String, String> addAttachFileRequestHeader(String s) {
        return imgHeader;
    }

}
