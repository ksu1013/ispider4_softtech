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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 전제현
 * @version 1.0 (2023-09-27)
 * @title Aspen Institute 수집 그룹 Extension (PXASPIS)
 * @since 2023-09-27
 */
public class AspenInsExtension implements Extension, AddonExtension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private String extensionName;
    private String cl_cd;
    private String origin_cd;
    private StringBuffer tagList;
    private boolean isTest;
    private Map<String, String> imgHeader;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        String bbsId = dqPageInfo.getBbsId();
        Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
        BbsSetting setting = Configuration.getInstance().getBbsSetting(bbsId);
        extensionName = setting.getExtensionName().replace("extension.", "");
        System.out.println("=== " + extensionName + " Start ===");
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        doc_id = 0;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(reposit);

        imgHeader = new HashMap<>();
        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        if (dqPageInfo.getParentUrl() == null) {    /* Page 0 LIST */
            if (url.contains("?page=")) {
                String[] urlArr = url.split("\\?page=");
                if (urlArr.length == 2) {
                    String pageUrl = urlArr[0];
                    String page = urlArr[1];
                    url = pageUrl + "/page/" + page + "/";
                }
            }
        }

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length()); // 초기화
        Document doc = Jsoup.parse(htmlSrc);
        if (dqPageInfo.getParentUrl() == null) {    /* Page 0 LIST */
            Elements liListElements = doc.select("body > main > div > div.block-container.js-filterPositioning__parent > div.container--post > ul > li");
            for (int idx = 0; idx < liListElements.size(); idx++) {
                Element liElement = liListElements.get(idx);
                Elements aTagElements = liElement.select("div > a");
                if (aTagElements.size() > 0) {
                    Element aTagElement = aTagElements.get(0);
                    String href = aTagElement.attr("href");
                    Elements titleElements = aTagElement.select("div.card__meta.u-issue__bg--before > div.card__title");
                    if (titleElements.size() > 0) {
                        Element titleElement = titleElements.get(0);
                        String title = titleElement.text();
                        tagList.append("<a href=\"" + href + "\">" + title + "</a>\n");
                    } else {
                        System.out.println(href + ": 제목 없음, 체크 필요");
                    }
                }
            }
        } else {    /* Page 1 CONTENT */
            String publishedTime = doc.getElementsByAttributeValue("property", "article:published_time").attr("content");
            String createdDate = publishedTime.replace("T", " ").replace("+00:00", "");
            tagList.append("<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n");
            Elements contentElements = doc.getElementsByClass("post__article__content");
            if (contentElements.size() > 0) {
                Element contentElement = contentElements.get(0);
                String content = contentElement.html();
                tagList.append("<CONTENT>" + content + "</CONTENT>");
            } else {
                Elements blockIntroElements = doc.getElementsByClass("block-intro__title");
                if (blockIntroElements.size() > 0) {
                    tagList.append("<CONTENT></CONTENT>");
                    System.out.println(dqPageInfo.getUrl() + ": 블록 처리된 게시물, 수집 불가");
                } else {
                    tagList.append("<CONTENT></CONTENT>");
                    System.out.println(dqPageInfo.getUrl() + ": 내용 없음, 체크 필요");
                }
            }
        }
        htmlSrc = tagList.toString();

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
                connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, null, "", documentId, cl_cd, origin_cd, "", imgHeader);  /* 이미지 처리 + 문서별 dqdoc 파일 저장 */
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
            e.printStackTrace();
        }

        return isCheck;
    }

    @Override
    public Map<String, String> addAttachFileRequestHeader(String url) {
        return imgHeader;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            if (!connectionUtil.isLocal() && !isTest) { /* 로컬 및 테스트 환경이 아니면 로그를 남긴다. */
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, "");  /* 수집로그 저장 */
            } else {
                connectionUtil.printCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, "");  /* 수집로그 예상 출력 */
            }
            //System.out.println("첨부파일 목록 : " + attaches_info.toString());  /* 삭제 예정 */
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
