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
import java.util.*;

/**
 * PXFMPRC
 * 중화인민공화국 외교부 수집 그룹
 * @version 1.0 (2023-06-07)
 * @since 2023-06-07
 * @author 전제현
 */
public class FmprcExtension implements Extension {

    private static String PAGE_URL_PARAMETER = "page=";
    private static String PXWJDT_PAGE_URL_ONE_PAGE = "https://www.fmprc.gov.cn/web/wjdt_674879/fyrbt_674889/index.shtml";
    private static String PXWJDT_PAGE_URL_TEMPLETE = "https://www.fmprc.gov.cn/web/wjdt_674879/fyrbt_674889/index_{page}.shtml";
    private static String PXZILIAO_PAGE_URL_ONE_PAGE = "https://www.fmprc.gov.cn/web/ziliao_674904/zyjh_674906/index.shtml";
    private static String PXZILIAO_PAGE_URL_TEMPLETE = "https://www.fmprc.gov.cn/web/ziliao_674904/zyjh_674906/index_{page}.shtml";
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
    private Map<String, String> header;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== FmprcExtension Start ===");
        Reposit bbsReposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
//        BbsSetting bbsSetting = Configuration.getInstance().getBbsSetting(dqPageInfo.getBbsId());
//        RobotConf robotConf = Configuration.getInstance().getRobotConf();
//        BbsPage pageList = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 0, "https://www.fnn.jp/category/news/%E5%85%A8%E5%9B%BD?page=1");
//        BbsPage pageContent = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 1, "*");
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(bbsReposit);

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        header = new HashMap<>();
        header.put("Host", "www.fmprc.gov.cn");
        header.put("Cookie", "HMF_CI=6593fea97c25921b6ec874c1f5834155fb2e3869232629bd7fe31481b74ae336760718c1db9c933de1f71e831caee679936a2493cf2394707c7b25ac2a24c32e1f; _trs_uv=lild3ae5_469_93qi; arialoadData=true; ariawapChangeViewPort=false; _trs_ua_s_1=lilgv9n8_469_cqr0");
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        header.put("User-Agent", commonUtil.generateRandomUserAgent());
		header.put("Accept-Encoding", "gzip, deflate, br");
		header.put("Accept-Language", "Ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7,zh-CN;q=0.6,zh;q=0.5,ja;q=0.4");
		header.put("Referer", "https://www.cato.org/search/issues/constitution-law?page=2");
		header.put("Cache-Control", "max-age=0");
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        if (dqPageInfo.getParentUrl() == null) {
            String page1Url = "";
            String pageUrlTemp = "";
            if (url.contains("wjdt_674879")) {
                page1Url = PXWJDT_PAGE_URL_ONE_PAGE;
                pageUrlTemp = PXWJDT_PAGE_URL_TEMPLETE;
            } else if (url.contains("ziliao_674904")) {
                page1Url = PXZILIAO_PAGE_URL_ONE_PAGE;
                pageUrlTemp = PXZILIAO_PAGE_URL_TEMPLETE;
            } else {
                return url;
            }

            if (url.contains(PAGE_URL_PARAMETER)) {
                String[] urlSplitArr = url.split(PAGE_URL_PARAMETER);
                if (urlSplitArr.length == 1) {
                    url = page1Url;
                } else {
                    String page = urlSplitArr[1];
                    if (!"0".equals(page)) {
                        url = pageUrlTemp.replace("{page}", page);
                    }
                }
            }
        }

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return header;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length()); // 초기화
        Document doc = Jsoup.parse(htmlSrc);
        if (dqPageInfo.getParentUrl() == null) {    // Page 0 LIST
            Elements newsBdClassElements = doc.getElementsByClass("newsBd");
            if (newsBdClassElements.size() > 0) {
                tagList.append(newsBdClassElements.html());
            } else {
                Elements reboxNewsClassElements = doc.getElementsByClass("rebox_news");
                if (reboxNewsClassElements.size() > 0) {
                    tagList.append(reboxNewsClassElements.html());
                }
            }
        } else {    // Page 1 CONTENT
            Elements newsDetailsElements = doc.getElementsByClass("news-details");
            Element newsDetailsElement = null;
            if (newsDetailsElements.size() > 0) {
                newsDetailsElement = newsDetailsElements.get(0);
            } else {
                Elements viboxElements = doc.getElementsByClass("vibox");
                if (viboxElements.size() > 0) {
                    newsDetailsElement = viboxElements.get(0);
                }
            }
            if (newsDetailsElement != null) {
                Elements newsTitleElements = newsDetailsElement.getElementsByClass("news-title");
                Element titleElement = null;
                String title = "";
                Element datetimeElement = null;
                String datetime = "";
                if (newsTitleElements.size() > 0) {
                    Element newsTitleElement = newsTitleElements.get(0);
                    titleElement = newsTitleElement.getElementsByTag("h1").get(0);
                    datetimeElement = newsTitleElement.getElementsByClass("time").get(0);
                    datetime = datetimeElement.text();
                } else {
                    titleElement = newsDetailsElement.getElementById("News_Body_Title");
                    datetimeElement = newsDetailsElement.getElementById("News_Body_Time");
                }
                title = titleElement.text();
                tagList.append("<TITLE>" + title + "</TITLE>");
                datetime = datetimeElement.text() + ":00";
                tagList.append("<CREATED_DATE>" + datetime + "</CREATED_DATE>");
                Element contentElement = newsDetailsElement.getElementById("News_Body_Txt_A");
                String content = contentElement.html();
                tagList.append("<CONTENT>" + content + "</CONTENT>");
            }
        }
        if (tagList.length() > 0) {
            htmlSrc = tagList.toString();
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
        String content = "";
        String url = "";
        String documentId = String.format("%06d", doc_id);

        Map<String, String> imageHeader = new HashMap<>();
        imageHeader.put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        imageHeader.put("Accept-Encoding", "gzip, deflate, br");
        imageHeader.put("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7,zh-CN;q=0.6,zh;q=0.5,ja;q=0.4");
        imageHeader.put("Cache-Control", "no-cache");
        imageHeader.put("Connection", "keep-alive");
        imageHeader.put("Cookie", "HMF_CI=6593fea97c25921b6ec874c1f5834155fb2e3869232629bd7fe31481b74ae336760718c1db9c933de1f71e831caee679936a2493cf2394707c7b25ac2a24c32e1f; _trs_uv=lild3ae5_469_93qi; arialoadData=true; ariawapChangeViewPort=false; _trs_ua_s_1=limq9dt9_469_ch79");
        imageHeader.put("Host", "www.fmprc.gov.cn");
        imageHeader.put("Pragma", "no-cache");
        imageHeader.put("User-Agent", commonUtil.generateRandomUserAgent());

        try {
            for (int i = 0; i < row.size(); i++) {
                String nodeName = row.getNodeByIdx(i).getName();
                String nodeValue = row.getNodeByIdx(i).getValue();
                if (nodeName.equals("title")) {
                    title = nodeValue;
                } else if (nodeName.equals("content")) {
                    content = nodeValue;
                } else if (nodeName.equals("url")) {
                    url = nodeValue;
                    imageHeader.put("Referer", url);
                }
            }

            if (title.equals("") || content.equals("")) {
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
            if (!connectionUtil.isLocal() && !isTest) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== FmprcExtension End ===");
        }
    }
}
