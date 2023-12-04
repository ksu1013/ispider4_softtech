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
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * JS1_PXEURO
 * @author 김승욱
 */
public class EuroExtension implements Extension, AddonExtension {

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

        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
        imgHeader.put("Cache-Control", "max-age=0");
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        this.url=url;
        return url;
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
        JSONObject jsonObject;
        JSONObject ResourceObject;
        JSONArray DocjsonObject;
        if (dqPageInfo.getParentUrl() == null){
            Document document = Jsoup.parse(htmlSrc);
            String domain= commonUtil.getDomain(url);
            urlList = document.select("body");
            try {
                jsonObject = (JSONObject) parser.parse(urlList.text());
                DocjsonObject = (JSONArray) jsonObject.get("docuLanguageListResources");
                tagList.append("<!--List Start-->");
                for (Object item : DocjsonObject){
                    JSONObject refCode= (JSONObject) item;
                    String reference= (String) refCode.get("refCode");
                    String language= (String) refCode.get("languageCode");
                    String titleCheck= (String) refCode.get("title");
                    if (tagList.indexOf(String.valueOf(reference)) == -1&&!titleCheck.contains("Daily News")) {
                        tagList.append("\n<a href =\"" + domain+"/commission/presscorner/api/documents?reference="+reference+"&language="+language+"\"></a>");
                    }
                }
                tagList.append("\n<!--List End-->");
                htmlSrc=tagList.toString();
                //System.out.println("htmlSrc = " + htmlSrc);

            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

        } else {
            /* 변수선언 start */
            String title="";
            String content = "";
            String datetime = "";
            String datetimeHtml="";
            /* 변수선언 end */

            /* 파싱 start */

            Document document = Jsoup.parse(htmlSrc);
            urlList=document.select("body");
            try {
                jsonObject = (JSONObject) parser.parse(urlList.text());
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

            ResourceObject = (JSONObject) jsonObject.get("docuLanguageResource");
            //title
            title= (String) ResourceObject.get("title");
            //System.out.println("title = " + title);
            //create date
            datetimeHtml = (String) jsonObject.get("publishDate");
            // 주어진 문자열을 ZonedDateTime 객체로 파싱
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(datetimeHtml);

            // ZonedDateTime 객체를 LocalDateTime 객체로 변환 (타임존 정보 제외)
            LocalDateTime localDateTime = zonedDateTime.toLocalDateTime();

            // 원하는 포맷으로 변환
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            datetime = localDateTime.format(formatter);
            //System.out.println("datetime = " + datetime);
            // content
            content=(String) ResourceObject.get("htmlContent");
            //System.out.println("content = " + content);

            /* 파싱 end */

            /* 태그 생성 start */
            htmlSrc = commonUtil.makeCollectContext(title, datetime, content);
            //System.out.println("htmlSrc = " + htmlSrc);
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

        //System.out.println("isCheck>>>>>>>>>>>> = " + isCheck);
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
