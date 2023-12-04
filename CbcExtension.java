package extension;


import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * JS1_PXCBC
 * @author 강태훈
 *
 */
public class CbcExtension implements Extension {

    private ConnectionUtil connectionUtil = new ConnectionUtil();
    private AttachUtil attachUtil = new AttachUtil();
    private String cl_cd;
    private String origin_cd;
    private boolean error_exist = false;
    private int doc_id;
    private List<HashMap<String, String>> attaches_info;
    private String now_time;
    private String file_name;
    private String content_url;

    private CommonUtil commonUtil;
    private StringBuffer tagList = new StringBuffer();
    private String TITLE;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("StartExtension!!!!!");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        Map<String, String> map = new HashMap<String, String>();

        System.out.println("addRequestHeader!!!!");
        return map;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        commonUtil = new CommonUtil();
        tagList.delete(0, tagList.length());    //초기화
        if(dqPageInfo.getParentUrl() == null) {
            String domain = commonUtil.getDomain(dqPageInfo.getUrl());

            // url List
            Document document = Jsoup.parse(htmlSrc);
            Elements urlList = document.select(".sclt-section0,.sclt-section1");
            urlList.select(".flag-video,.flag-cbc-kids-news").remove();
            urlList = urlList.select("a[href]");

            tagList.append("<!--List Start-->");
            for (Element link : urlList) {
                tagList.append("\n<a href =\"" + domain + link.attr("href") + "\"></a>");
            }

            tagList.append("\n<!--List End-->");
            htmlSrc = tagList.toString();

            System.out.println(htmlSrc);
        } else {
            /* 변수선언 start */
            String title = "";
            String content = "";
            String datetime = "";
            /* 변수선언 end */

            /* 파싱 start */
            Document document = Jsoup.parse(htmlSrc);

            // title
            title = document.getElementsByClass("detailHeadline").text();

            // cont
            if(document.getElementsByClass("storyWrapper").size() > 0){
                Element contentElements = document.getElementsByClass("storyWrapper").get(0);
                contentElements.select(".image-caption,.media-caption,.video-info-container,.commentCount-text-container").remove();
                content = contentElements.toString();
            }

            // created_date
            System.out.println("★ : " + document.getElementsByAttributeValue("property", "article:modified_time"));

            String datetimeHtml = document.select(".bylineDetails .timeStamp").attr("datetime");
            if(datetimeHtml != null && !datetimeHtml.equals("")) {
                LocalDateTime localtime = LocalDateTime.from(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(datetimeHtml)).atZone(ZoneId.of("Asia/Seoul")));
                datetime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(localtime);
            }
            /* 파싱 end */

            /* 태그 생성 start */

            tagList.append("<TITLE>" + title + "</TITLE>\n");
            tagList.append("<CONTENT>" + content + "</CONTENT>\n");
            tagList.append("<DATETIME>" + datetime + "</DATETIME>\n");

            htmlSrc = tagList.toString();

//			System.out.println(tagList);
            /* 태그 생성 end */
        }

        return htmlSrc;
    }

    @Override
    public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
            // doc_id, cl_cd, origin_cd 번호 넣어주는 부분
        doc_id++;
        for (int i = 0; i < row.size(); i++) {
            String nodeId = row.getNodeByIdx(i).getId();
            String nodeName = row.getNodeByIdx(i).getName();
            String nodeValue = row.getNodeByIdx(i).getValue();

            if (nodeName.equals("source_class")) {
                cl_cd = nodeValue;
            } else if (nodeName.equals("source_ID")) {
                origin_cd = nodeValue;
            }

            if (nodeName.equals("document_id")) {
                row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
            }

        }
    }

    @Override
    public boolean validData(Row row, DqPageInfo dqPageInfo) {
        boolean isCheck = true;
        //현재 문서 첨부파일
        List<String> content_images = new ArrayList<>();

        try {
            String title="";

            for (int i = 0; i < row.size(); i++) {
                String nodeId = row.getNodeByIdx(i).getId();
                String nodeName = row.getNodeByIdx(i).getName();
                String nodeValue = row.getNodeByIdx(i).getValue();

                if (nodeName.equals("title") ) {
                    title = nodeValue;
                }


                if(nodeName.equals("images") && nodeValue != null && !nodeValue.equals("")) {
                    HashMap<String, String> attach = new HashMap<>();

                    content_images = connectionUtil.setAttachImageValue(attaches_info, attach, nodeValue, doc_id);

                    file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);

                    //첨부파일명 저장
                    String attachFiles = connectionUtil.setAttachNode(file_name, attach);
                    System.out.println(attachFiles);
                    row.getNodeByIdx(i).clearValue();
                    row.getNodeByIdx(i).setValue(attachFiles);
                }
            }

            //=====================================================
            //현재 문서 첨부파일 매핑 위치 설정
            for(int i=0; i<row.size(); i++) {
                String nodeId = row.getNodeByIdx(i).getId();
                String nodeName = row.getNodeByIdx(i).getName();
                String nodeValue = row.getNodeByIdx(i).getValue();
                List<String> image_list = new ArrayList<>();		//본문에서 추출한 이미지 태그 목록

                if(nodeName.equals("content") && nodeValue != null && !nodeValue.equals("")) {
                    String content = connectionUtil.getContainImageContent(nodeValue, content_images);

                    row.getNodeByIdx(i).clearValue();
                    row.getNodeByIdx(i).setValue(content);
                }
            }
            //=====================================================

            if(title.equals("")) {
                isCheck = false;
                connectionUtil.upFailDocFileDownloadCount();	//에러 파일수 판단용
            }

        }catch(Exception e) {
            e.printStackTrace();
        }
        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
        String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);

        // 수집로그 저장
        connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);

        connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);

        System.out.println("첨부파일 목록 : " + attaches_info.toString());

        // 첨부파일 저장
        connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
    }
}
