package extension;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.diquest.ispider.common.conf.setting.BbsSetting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

/**
 * JS1_PXBROOKS
 *
 * @author 이가원
 */
public class BrookingsExtension implements Extension {

	private CommonUtil commonUtil;
	private ConnectionUtil connectionUtil;
	private int doc_id;
	private String extensionName;
	private String cl_cd;
	private String origin_cd;
	private StringBuffer tagList;
	private boolean isTest;

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
		tagList = new StringBuffer();
		isTest = connectionUtil.isTest(reposit);
	}

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        Map<String, String> map = new HashMap<String, String>();

        map.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
        return map;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        try {
            Document document = Jsoup.parse(htmlSrc);
            if (dqPageInfo.getParentUrl() != null && !dqPageInfo.getParentUrl().equals("")) {
                //title
                String title = "";
                if (document.getElementsByTag("h1") != null && document.getElementsByTag("h1").size() > 0 && title.equals("")) {
                    title = document.getElementsByTag("h1").get(0).text();
                } else if (document.getElementsByTag("title") != null && document.getElementsByTag("title").size() > 0 && title.equals("")) {
                    title = document.getElementsByTag("title").text();
                } else if (document.getElementsByClass("monitor-header-title") != null && title.equals("")) {
                    title = document.getElementsByClass("monitor-header-title").get(0).text();
                }

                //content
                String content = "";
                document.getElementsByClass("contact").remove();
                if (document.getElementsByClass("core-block") != null && document.getElementsByClass("core-block").size() > 0) {
                    content = document.getElementsByClass("core-block").outerHtml();
                } else if (document.getElementsByClass("post-body") != null && document.getElementsByClass("post-body").size() > 0 && content.equals("")) {
                    content = document.getElementsByClass("post-body").outerHtml();
                } else if (document.getElementsByClass("content-column") != null && document.getElementsByClass("content-column").size() > 0 && content.equals("")) {
                    content = document.getElementsByClass("content-column").outerHtml();
                }

                //date
                String datetime = "";
                if (document.getElementsByAttributeValue("property", "article:modified_time") != null && document.getElementsByAttributeValue("property", "article:modified_time").size() > 0 && datetime.equals("")) {
                    datetime = document.getElementsByAttributeValue("property", "article:modified_time").get(0).attr("content");
                } else if (document.getElementsByTag("time") != null && document.getElementsByTag("time").size() > 0) {
                    Element date_element = document.getElementsByTag("time").get(0);
                    if (date_element.attr("content") != null && !date_element.attr("content").equals("") && datetime.equals("")) {
                        datetime = date_element.attr("content");
                    } else {
                        datetime = date_element.text();
                    }
                } else if (document.getElementsByClass("block--essay-pub-date") != null && document.getElementsByClass("block--essay-pub-date").size() > 0 && datetime.equals("")) {
                    datetime = document.getElementsByClass("block--essay-pub-date").text();
                }

                htmlSrc = "<cont_title>" + title + "</cont_title><cont_conts>" + content + "</cont_conts><cont_date>" + datetime + "</cont_date>";

            } else {
                //authors 제거
                document.getElementsByClass("authors").remove();
                //label 제거
                document.getElementsByClass("label").remove();

                htmlSrc = "<div class=\"list-content\">";
                htmlSrc += document.getElementsByTag("article").toString();
                htmlSrc += "<footer class=\"site-footer\">";
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            } else if (nodeName.equals("created_date") && !nodeValue.equals("") && nodeValue != null) {
                LocalDateTime localtime = LocalDateTime.from(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(nodeValue)).atZone(ZoneId.of("Asia/Seoul")));
                nodeValue = localtime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                System.out.println("datetime : " + nodeValue);
                row.getNodeByIdx(i).setValue(nodeValue.replace("T", " "));
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
                connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, null, "", documentId, cl_cd, origin_cd, "");
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
            e.printStackTrace();
        }

        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            if (!connectionUtil.isLocal() && !isTest) { /* 로컬 및 테스트 환경이 아니면 로그를 남긴다. */
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, "");  /* 수집로그 저장 */
            } else {
                connectionUtil.printCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, "");  /* 수집로그 예상 출력 */
            }
            connectionUtil.moveAttachFile(dqPageInfo.getBbsId(), isTest);   /* 2023-09-21 jhjeon: 문서별 첨부파일명 변경 및 이동 처리 로직 추가 */
            connectionUtil.moveDqdocFile(dqPageInfo.getBbsId(), isTest);    /* 2023-10-26 jhjeon: DQDOC 문서 이동 처리 로직 추가 */
            connectionUtil.printFilesInfo();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== " + extensionName + " End ===");
        }
    }
}
