package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JS1_PXBEIJINGGOV
 *
 * @author 김승욱
 * 사이트에서 공격으로 의심해서 벤 함
 */
public class BeijingGovExtension implements Extension, AddonExtension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private String extensionName;
    private String cl_cd;
    private String origin_cd;
    private String now_time;
    private String file_name;
    private StringBuffer tagList;
    private boolean error_exist;
    private boolean isTest;
    private Map<String, String> imgHeader;
    private List<HashMap<String, String>> attaches_info;
    private String url;
    private Random random;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        String bbsId = dqPageInfo.getBbsId();
        Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
        BbsSetting setting = Configuration.getInstance().getBbsSetting(bbsId);
        extensionName = setting.getExtensionName().replace("extension.", "");
        System.out.println("=== " + extensionName + " Start ===");
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
        random = new Random();

        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
        imgHeader.put("Cache-Control", "max-age=0");
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        if (url.contains("?page=")) {
            String lastSegment = url.substring(url.lastIndexOf('/') + 1);       // URL에서 마지막 '/' 이후의 문자열을 가져옵니다.
            String number = lastSegment.substring(lastSegment.indexOf('=') + 1);    // 문자열 "page=" 다음의 숫자를 가져옵니다.
            if (!number.equals("0")) {  /* page 1부터 index_{number}.html 형식으로 url 수정 */
                url = url.replace("index.html?page=" + number, "/index_" + number + ".html");
            }
        }
        this.url = url;

        int delay = random.nextInt(1000) + 1000;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length());
        if (dqPageInfo.getParentUrl() == null) {
            Document document = Jsoup.parse(htmlSrc);
            Elements urlList = document.select("div[class=listBox]").select("ul");
            urlList = urlList.select("a[href]");
            tagList.append("<!--List Start-->");
            for (Element link : urlList) {
                String currentUrl = link.attr("href");
                currentUrl = currentUrl.replace("./", "https://www.beijing.gov.cn/zhengce/zhengcefagui/");
                tagList.append("\n<a href =\"" + currentUrl + "\">link</a>");
            }
            tagList.append("\n<!--List End-->");
            htmlSrc = tagList.toString();   /* 2023-09-14 jhjeon 추가 */
        } else {
            /* 변수선언 start */
            String title = "";
            String content = "";
            String datetime = "";
            String datetimeHtml = "";
            /* 변수선언 end */
            /* 파싱 start */
            Document document = Jsoup.parse(htmlSrc);
            //System.out.println("document = " + document);
            title = document.select("div[class=header]").select("h1").text();
            //System.out.println("title = " + title);
            //create date
            datetimeHtml = document.select("meta[name=PubDate]").attr("content");
            /**
             * 2023-09-14 jhjeon: 기존 구현 부분은 주석처리함
             * */
//            // SimpleDateFormat 객체 생성
//            SimpleDateFormat originalDateFormat = new SimpleDateFormat("yyyy-MM-dd");
//            SimpleDateFormat targetDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//
//            // 원본 날짜 문자열을 Date 객체로 파싱
//            Date originalDate = null;
//            try {
//                originalDate = originalDateFormat.parse(datetimeHtml);
//            } catch (ParseException e) {
//                throw new RuntimeException(e);
//            }
//            // 새로운 형식으로 날짜 포맷팅
//            datetime = targetDateFormat.format(originalDate);
//            //System.out.println("datetime = " + datetime);
            Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
            Matcher matcher = pattern.matcher(datetimeHtml);
            while (matcher.find()) {
                datetime = matcher.group();
            }
            datetime += ":00";

            // content
            Elements contentHtml = document.select("div[id=mainText]"); //본문내용
            Elements docFilecontentHtml = document.select("ul[class=fujian]");// 첨부파일

            //첨부파일 여부 체크
            if (!docFilecontentHtml.text().equals("")) {
                content = contentHtml.toString() + docFilecontentHtml.toString();
            } else {
                content = contentHtml.toString();
            }

            //System.out.println("content = " + content);
            /* 파싱 end */

            /* 태그 생성 start */
            htmlSrc = commonUtil.makeCollectContext(title, datetime, content);
            //System.out.println("htmlSrc = " + htmlSrc);
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
            System.out.println("=== " + extensionName + " End ===");
        }
    }

    @Override
    public Map<String, String> addAttachFileRequestHeader(String url) {
        return imgHeader;
    }
}
