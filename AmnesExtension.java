package extension;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.diquest.ispider.common.conf.setting.BbsSetting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;

/**
 * JS1_PXAMNE
 *
 * @author 이가원
 */
public class AmnesExtension implements Extension, AddonExtension {

    private ConnectionUtil connectionUtil = new ConnectionUtil();
    private int doc_id;
    private String extensionName;
    private String cl_cd;
    private String origin_cd;
    private List<HashMap<String, String>> attaches_info;
    private SimpleDateFormat dtFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH);
    private SimpleDateFormat newDtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private boolean error_exist = false;
    private boolean isTest;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        String bbsId = dqPageInfo.getBbsId();
        Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
        BbsSetting setting = Configuration.getInstance().getBbsSetting(bbsId);
        extensionName = setting.getExtensionName().replace("extension.", "");
        System.out.println("=== " + extensionName + " Start ===");

        doc_id = 0;
        attaches_info = new ArrayList<>();
        isTest = connectionUtil.isTest(reposit);
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        /*
         * pageNum에 page 파라미터 값 넣어주기
         * https://www.amnesty.org/en/latest/page/pageNum/?pg=*
         */
        try {
            if (dqPageInfo.getParentUrl() == null) {
                if (url.contains("?pg=")) {
                    String[] urls = url.split("pg=");
                    url = url.replace("pageNum", urls[1]);

                    System.out.println("변경된 url : " + url);
                }
            } else {
                url = dqPageInfo.getUrl();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

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
        Document document = Jsoup.parse(htmlSrc);
        if (dqPageInfo.getParentUrl() != null && !dqPageInfo.getParentUrl().equals("")) {
            //title, date => jsoup에서 tag로 추출해서 html에 붙여주기
            String title = "<cont_h1>" + document.getElementsByTag("h1").text() + "</cont_h1>";
            String content = "";
            if (document.getElementsByClass("article-content ") != null && document.getElementsByClass("article-content ").size() > 0 && content.equals("")) {
                content = document.getElementsByClass("article-content ").outerHtml();
            }
            String date = "";

            if (document.getElementsByTag("time").size() > 0) {
                date = document.getElementsByTag("time").text();
            } else if (document.getElementsByAttributeValue("aria-label", "Post published timestamp").size() > 0) {
                date = document.getElementsByAttributeValue("aria-label", "Post published timestamp").text();
            }

            try {
                Date formatDate = null;
                if (date != null && !date.equals("")) {
                    formatDate = dtFormat.parse(date);
                }
                date = "<cont_date>" + newDtFormat.format(formatDate) + "</cont_date>";

            } catch (ParseException e) {
                e.printStackTrace();
            }

            htmlSrc = title + content + date;
        } else {
            //post-category 제거
            document.getElementsByClass("post-category").remove();

            Elements a_list = document.getElementsByClass("post-title");
            htmlSrc = "<div class=\"postlist\">\n";
            for (int i = 0; i < a_list.size(); i++) {
                String href = a_list.get(i).getElementsByTag("a").attr("href");
                htmlSrc += "<a href=\"" + href + "\"></a>\n";
            }
            htmlSrc += "</section>";
            //htmlSrc = document.html();
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
                connectionUtil.upFailDocFileDownloadCount(); //  에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, null, "", documentId, cl_cd, origin_cd, "");
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount(); //  에러 파일수 판단용
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

    @Override
    public Map<String, String> addAttachFileRequestHeader(String url) {
        if (url.contains("amnesty.org"))
            return getHeader();
        return null;
    }

    public HashMap<String, String> getHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Accept-Language", "ko-KR,ko;q=0.9");
        headers.put("Connection", "keep-alive");
        headers.put("Cookie", "CookieControl={\"necessaryCookies\":[\"*ordpress_*\",\"*ordpress_logged_in_*\",\"CookieControl\",\"__cfduid\"],\"optionalCookies\":{},\"statement\":{\"shown\":true,\"updated\":\"18/01/2021\"},\"consentDate\":1681446357301,\"consentExpiry\":90,\"interactedWith\":true,\"user\":\"C93F354B-A7E4-4A52-8BE8-BA5CF2E39C43\"}");
        headers.put("Host", "www.amnesty.org");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");

        return headers;
    }
}
