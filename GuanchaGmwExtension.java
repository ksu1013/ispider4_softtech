package extension;

import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * PXGUANCHAGMW
 * 광밍넷 수집 그릅
 * gmw 수집
 * https://guancha.gmw.cn/node_11273.htm
 * @Since 2023-05-17
 * @author 전제현
 */
public class GuanchaGmwExtension implements Extension {

    private static final String DOMAIN_DEFAULT_URL = "https://guancha.gmw.cn/";
    private static final String LIST_DEFAULT_URL = DOMAIN_DEFAULT_URL + "node_11273_{page}.htm";
    private ConnectionUtil connectionUtil;
    private CommonUtil commonUtil;
    private String cl_cd;
    private String origin_cd;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private boolean error_exist;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== GuanchaGmwExtension Start ===");
        connectionUtil = new ConnectionUtil();
        commonUtil = new CommonUtil();
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;

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
        return map;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {

        String newHtmlSrc = "";
        if (dqPageInfo.getParentUrl() == null) {    /* LIST PAGE */
            String url = dqPageInfo.getUrl();
            String realUrl = "";
            Map<String, String> params = commonUtil.getUrlQueryParams(url);
            String page = params.get("page");
            if (StringUtils.equals(page, "1")) {
                realUrl = LIST_DEFAULT_URL.replace("_{page}", "");
            } else {
                realUrl = LIST_DEFAULT_URL.replace("{page}", page);
            }
            newHtmlSrc = "<PAGE-LIST>\n";
            try {
                Document doc = null;
                if (connectionUtil.isProxy()) {
                    String proxyIP = connectionUtil.getProxyIp();
                    String proxyPortStr = connectionUtil.getProxyPort();
                    int proxyPort = Integer.parseInt(proxyPortStr);
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIP, proxyPort));
                    doc = Jsoup.connect(realUrl).proxy(proxy).get();
                } else {
                    doc = Jsoup.connect(realUrl).get();
                }
                if (StringUtils.equals(page, "1")) {    /* 1페이지만 select된 기사를 가져온다. */
                    Element articleElement = doc.getElementsByClass("select").get(0);
                    Element article1LinkElement = articleElement.getElementsByTag("a").get(0);
                    Element article2Element = articleElement.getElementsByClass("reading_box").get(0);
                    Element article2LinkElement = article2Element.getElementsByTag("a").get(1);
                    String article1Href = article1LinkElement.attr("href");
                    if (!article1Href.contains("https://")) {
                        article1Href = DOMAIN_DEFAULT_URL + article1Href;
                    }
                    newHtmlSrc += "<a href='" + article1Href + "'>link</a>\n";
                    String article2Href = article2LinkElement.attr("href");
                    if (!article2Href.contains("https://")) {
                        article2Href = DOMAIN_DEFAULT_URL + article2Href;
                    }
                    newHtmlSrc += "<a href='" + article2Href + "'>link</a>\n";
                }
                Element ulElement = doc.getElementsByClass("content_left_main").get(0);
                Elements liElements = ulElement.getElementsByTag("li");
                for (int cnt = 0; cnt < liElements.size(); cnt++) {
                    Element articleElement = liElements.get(cnt);
                    Element mainTitleElement = articleElement.getElementsByClass("main_title").get(0);
                    Element article1LinkElement = mainTitleElement.getElementsByTag("a").get(0);
                    String article1Href = article1LinkElement.attr("href");
                    if (!article1Href.contains("https://")) {
                        article1Href = DOMAIN_DEFAULT_URL + article1Href;
                    }
                    newHtmlSrc += "<a href='" + article1Href + "'>link</a>\n";
                    Element article2Element = articleElement.getElementsByClass("reading_box").get(0);
                    Elements article2LinkElements = article2Element.getElementsByTag("a");
                    if (article2LinkElements.size() > 1) {
                        Element article2LinkElement = article2LinkElements.get(1);
                        String article2Href = article2LinkElement.attr("href");
                        if (!article2Href.contains("https://")) {
                            article2Href = DOMAIN_DEFAULT_URL + article2Href;
                        }
                        newHtmlSrc += "<a href='" + article2Href + "'>link</a>\n";
                    } else if (article2LinkElements.size() == 1) {
                        Element article2LinkElement = article2LinkElements.get(0);
                        String article2Href = article2LinkElement.attr("href");
                        if (!StringUtils.equals(article2Href, "ArticleUrlPh")) {
                            if (!StringUtils.contains(article2Href, "https://")) {
                                article2Href = DOMAIN_DEFAULT_URL + article2Href;
                            }
                            newHtmlSrc += "<a href='" + article2Href + "'>link</a>\n";
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            newHtmlSrc += "</PAGE-LIST>\n";
        } else {    /* CONTENT PAGE */
            String url = dqPageInfo.getUrl();
            newHtmlSrc = "<PAGE-CONTENT>\n";
            Document doc = Jsoup.parse(htmlSrc);
            /* TITLE 수집 */
            Element titleElement = doc.getElementsByClass("u-title").get(0);
            String title = titleElement.text();
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
            /* CONTENT 수집 */
            Element contentElement = doc.getElementById("articleBox");
            String content = contentElement.html();
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
            /* CREATED_DATE 수집 */
            if (StringUtils.contains(url, "https://m.gmw.cn/")) {
                Element datetimeElement = doc.getElementsByClass("m-contentMsg").get(0);
                datetimeElement.getElementsByTag("span").remove();
                String createdDate = datetimeElement.text() + ":00";
                newHtmlSrc += "<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n";
            } else {
                Element datetimeElement = doc.getElementsByClass("m-con-time").get(0);
                String createdDate = datetimeElement.text() + ":00";
                newHtmlSrc += "<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n";
            }
            newHtmlSrc += "</PAGE-CONTENT>\n";
        }

        return newHtmlSrc;
    }

    @Override
    public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
        List<String> urls = new ArrayList<String>();
        urls.add(naviUrl);
        return urls;
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
        String documentId = String.format("%06d", doc_id);

        try {
            for (int i = 0; i < row.size(); i++) {
                String nodeName = row.getNodeByIdx(i).getName();
                String nodeValue = row.getNodeByIdx(i).getValue();
                if (nodeName.equals("title")) {
                    title = nodeValue;
                    break;
                }
            }

            if (title.equals("")) {
                isCheck = false;
                connectionUtil.upFailDocFileDownloadCount();	// 에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();	// 에러 파일수 판단용
            e.printStackTrace();
        }

        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
            if (!connectionUtil.isLocal()) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== GuanchaGmwExtension End ===");
        }
    }

    public static void main(String[] args) {
        contentTest();
    }

    private static void listTest() {
        String newHtmlSrc = "";
        String realUrl = "https://guancha.gmw.cn/node_11273.htm";
        String page = "1";
        newHtmlSrc = "<PAGE-LIST>\n";
        try {
            Document doc = Jsoup.connect(realUrl).get();
            if (StringUtils.equals(page, "1")) {    /* 1페이지만 select된 기사를 가져온다. */
                Element articleElement = doc.getElementsByClass("select").get(0);
                Element article1LinkElement = articleElement.getElementsByTag("a").get(0);
                Element article2Element = articleElement.getElementsByClass("reading_box").get(0);
                Element article2LinkElement = article2Element.getElementsByTag("a").get(1);
                String article1Href = article1LinkElement.attr("href");
                if (!article1Href.contains("https://")) {
                    article1Href = DOMAIN_DEFAULT_URL + article1Href;
                }
                newHtmlSrc += "<a href='" + article1Href + "'>link</a>\n";
                String article2Href = article2LinkElement.attr("href");
                if (!article2Href.contains("https://")) {
                    article2Href = DOMAIN_DEFAULT_URL + article2Href;
                }
                newHtmlSrc += "<a href='" + article2Href + "'>link</a>\n";
            }
            Element ulElement = doc.getElementsByClass("content_left_main").get(0);
            Elements liElements = ulElement.getElementsByTag("li");
            for (int cnt = 0; cnt < liElements.size(); cnt++) {
                Element articleElement = liElements.get(cnt);
                Element mainTitleElement = articleElement.getElementsByClass("main_title").get(0);
                Element article1LinkElement = mainTitleElement.getElementsByTag("a").get(0);
                Element article2Element = articleElement.getElementsByClass("reading_box").get(0);
                Element article2LinkElement = article2Element.getElementsByTag("a").get(1);
                String article1Href = article1LinkElement.attr("href");
                if (!article1Href.contains("https://")) {
                    article1Href = DOMAIN_DEFAULT_URL + article1Href;
                }
                newHtmlSrc += "<a href='" + article1Href + "'>link</a>\n";
                String article2Href = article2LinkElement.attr("href");
                if (!article2Href.contains("https://")) {
                    article2Href = DOMAIN_DEFAULT_URL + article2Href;
                }
                newHtmlSrc += "<a href='" + article2Href + "'>link</a>\n";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        newHtmlSrc += "</PAGE-LIST>\n";
        System.out.println(newHtmlSrc);
    }

    private static void contentTest() {
        String newHtmlSrc = "";
        String realUrl = "https://guancha.gmw.cn/2023-05/17/content_36566763.htm";
        String page = "1";
        newHtmlSrc = "<PAGE-CONTENT>\n";
        try {
            Document doc = Jsoup.connect(realUrl).get();
            /* TITLE 수집 */
            Element titleElement = doc.getElementsByClass("u-title").get(0);
            String title = titleElement.text();
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
            /* CONTENT 수집 */
            Element contentElement = doc.getElementById("articleBox");
            String content = contentElement.html();
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
            /* CREATED_DATE 수집 */
            Element datetimeElement = doc.getElementsByClass("m-con-time").get(0);
            String createdDate = datetimeElement.text() + ":00";
            newHtmlSrc += "<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n";
        } catch (IOException e) {
            e.printStackTrace();
        }
        newHtmlSrc += "</PAGE-CONTENT>\n";
        System.out.println(newHtmlSrc);
    }
}
