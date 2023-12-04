package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JS1_PXFRANCE
 * @author 김승욱
 */
public class FranceExtension implements Extension, AddonExtension {

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

    //오늘날짜
    private LocalDate today = LocalDate.now();
    // 하루 전 날짜 계산
    private LocalDate yesterday ;
    private Elements dataTestList;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== DefaultExtension Start ===");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
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
        if (dqPageInfo.getParentUrl() == null){
            Document document = Jsoup.parse(htmlSrc);
            urlList = document.select("a[name=top]").parents();
            urlList.select("p[align=center]").remove();
            getUrlList2(urlList);
            htmlSrc=getUrlList(urlList,url);
            htmlSrc = htmlSrc.replace("#", "&");
            //System.out.println("htmlSrc = " + htmlSrc);
        } else {
            /* 변수선언 start */
            String title="";
            String content = "";
            String datetime = "";
            String[] approachsUrl;
            /* 변수선언 end */

            approachsUrl=url.split("&");
            String docTest=commonUtil.getSubStringResult("<a name="+approachsUrl[1]+">","<p align='center'>",htmlSrc);

            /* 파싱 start */
            Document document = Jsoup.parse(docTest);
            //System.out.println("document = " + document);
            title=document.getElementsByTag("strong").first().text();
            // System.out.println("title = " + title);

            //create date
            Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
            Matcher matcher = pattern.matcher(url);

            if (matcher.find()) {
                datetime = matcher.group();
                //    System.out.println("Extracted Date: " + datetime);
            } else {
                System.out.println("Date not found in the string.");
            }

            // content
            Elements contentHtml = document.select("body");
            contentHtml.select("strong").first().remove();
            //System.out.println("contentHtml = " + contentHtml);

            content=contentHtml.toString();
            //System.out.println("contentHtml = " + contentHtml);
            /* 파싱 end */

            /* 태그 생성 start */
            htmlSrc = commonUtil.makeCollectContext(title, datetime, content);
            //System.out.println("htmlSrc = " + htmlSrc);
            /* 태그 생성 end */
        }
        return htmlSrc;
    }

    private void getUrlList2(Elements dateList) {
        Elements dataLists = dateList.select("option[value]");
        this.dataTestList=dataLists;
    }

    @Override
    public List<String> makeNewUrls(String maviUrl, DqPageInfo dqPageInfo) {
        //System.out.println("dataTestList = " + dataTestList);
        List<String> urllists = new ArrayList<String>();

        maviUrl="https://basedoc.diplomatie.gouv.fr/vues/Kiosque/FranceDiplomatie/kiosque.php?fichier=";
        Elements dataLists = dataTestList.select("option[value]");
        for (Element link : dataLists) {
            String currentUrl = link.attr("value");
            if(!currentUrl.equals(""))urllists.add( maviUrl+currentUrl );
        }
        return urllists;
        //return null;
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
            connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
            if (title.equals("") || content.equals("")) {
                isCheck = false;
                connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
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

    // 0뎁스 LIST 만들기 (Jsoup Elements)
    public String getUrlList(Elements urlList, String domain) {
        StringBuffer tagList = new StringBuffer();
        urlList = urlList.select("a[href]");

        tagList.append("<!--List Start-->");
        for (Element link : urlList) {
            String currentUrl = link.attr("href");
            if(!currentUrl.contains(".pdf")){
                tagList.append("\n<a href =\"" + domain+ currentUrl + "\"></a>");
            }
        }
        tagList.append("\n<!--List End-->");

        return tagList.toString();
    }
}
