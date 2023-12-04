package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;

import java.text.SimpleDateFormat;
import java.util.*;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 아사히 신문
 */
public class AsahiExtension implements Extension, AddonExtension {

    private ConnectionUtil connectionUtil;
    private String cl_cd;
    private String origin_cd;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private boolean error_exist;
    private StringBuffer tagList;
    private CommonUtil commonUtil;
    private Map<String, String> imgHeader;
    private boolean isTest;
    private String url;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== AsahiExtension Start ===");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        error_exist = false;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(reposit);
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);
        imgHeader = new HashMap<>();
        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
        imgHeader.put("Cache-Control", "max-age=0");
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        this.url = url;
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        Map<String, String> map = new HashMap<String, String>();
        return map;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length()); // 초기화
        if (dqPageInfo.getParentUrl() == null) {
            String domain = commonUtil.getDomain(url);
            Document document = Jsoup.parse(htmlSrc);
            Elements urlList = document.select("ul[class=List]").select("li");
            htmlSrc = getUrlList(urlList, domain);
        } else { // 1depth
            /* 변수선언 start */
            Document doc = Jsoup.parse(htmlSrc);
            String newHtmlSrc = "<CONTENT-PAGE>\n";
            /* 제목(title) 수집 */
            String titleArea = doc.select("title").first().ownText();
            String title = titleArea;
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
            /* 내용(content) 수집 */

            String content = "";

            Elements Images = doc.getElementsByClass("webHonbun");
            content += Images.html();

            Elements contentElement = doc.getElementsByClass("article-body");
            content += contentElement.html();
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";

            /* 생성일(created_date) 수집 */
            Elements metaTags = doc.select("meta[name=release_date]");

            // 메타 태그에서 content 속성 값 가져오기
            String releaseDate = "";
            if (!metaTags.isEmpty()) {
                Element metaTag = metaTags.first();
                releaseDate = metaTag.attr("content");
            }
            releaseDate = releaseDate + " 00:00:00";
            newHtmlSrc += "<CREATED_DATE>" + releaseDate + "</CREATED_DATE>\n";
            newHtmlSrc += "</CONTENT-PAGE>";
            htmlSrc = newHtmlSrc;

        }
        return htmlSrc;
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
                connectionUtil.upFailDocFileDownloadCount(); //  에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
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
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
            /* 수집로그 저장 */
            if (!connectionUtil.isLocal() && !isTest) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            /* 첨부파일 저장 */
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== AsahiExtension end ===");
        }
    }

    @Override
    public Map<String, String> addAttachFileRequestHeader(String s) {
        return imgHeader;
    }

    // 0뎁스 LIST 만들기 (Jsoup Elements)
    private String getUrlList(Elements urlList, String domain) {
        StringBuffer tagList = new StringBuffer();
        urlList = urlList.select("a[href]");

        tagList.append("<!--List Start-->");
        for (Element link : urlList) {
            String currentUrl = link.attr("href");
            if (!currentUrl.equals("/")) {
                if (currentUrl.contains(domain)) {
                    tagList.append("\n<a href =\"" + currentUrl + "\"></a>");
                } else {
                    tagList.append("\n<a href =\"" + domain + currentUrl + "\"></a>");
                }
            }
        }
        tagList.append("\n<!--List End-->");

        return tagList.toString();
    }
}
