package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author 전제현
 * @version 1.0 (2023-09-14)
 * @title 중국신문망 수집 Extension (PXGDCH)
 * @since 2023-09-14
 */
public class GdchExtension implements Extension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private int checkBlankLiCnt;    /* 빈 li 체크용 count (해당 카운트만큼의 li 배치 다음에 해당되는 li마다 빈값이 들어 있어 추가함.) */
    private int page;
    private int len;
    private String extensionName;
    private String cl_cd;
    private String origin_cd;
    private String now_time;
    private String file_name;
    private StringBuffer tagList;
    private boolean isTest;
    private boolean error_exist;
    private List<HashMap<String, String>> attaches_info;

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
        page = 1;
        len = 40;
        checkBlankLiCnt = 5;    /* li 배치 패턴이 변경되지 않는 이상 수정하지 말 것, 2023-09-14 기준 li 5개마다 다음 li는 빈값임 */
        attaches_info = new ArrayList<>();
        error_exist = false;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(reposit);

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {

        if (url.contains("?page=")) {
            String[] urlArr = url.split("\\?");
            String params = urlArr[1];
            String[] paramsArr = params.split("&");
            for (String param : paramsArr) {
                if (param.contains("page=")) {
                    String temp = param.replace("page=", "");
                    page = Integer.parseInt(temp);
                } else if (param.contains("len=")) {
                    String temp = param.replace("len=", "");
                    len = Integer.parseInt(temp);
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
            Element ulListElement = doc.getElementById("listContainer");
            Elements liElements = ulListElement.getElementsByTag("li");
            int totalBlankLi = len / checkBlankLiCnt;
            int startCnt = (len + totalBlankLi) * (page - 1);
            int endCnt = ((len + totalBlankLi) * page) - 1;
            if (endCnt > liElements.size()) {
                endCnt = liElements.size();
            }

            if (startCnt < endCnt) {
                for (int cnt = startCnt; cnt < endCnt; cnt++) {
                    Element liElement = liElements.get(cnt);
                    Elements aElements = liElement.getElementsByTag("a");
                    if (aElements.size() > 0) {
                        Element aElement = aElements.get(0);
                        String href = aElement.attr("href");
                        String title = aElement.text();
                        tagList.append("<a href='" + href + "'>" + title + "</a>\n");
                    }
                }
            }
            htmlSrc = tagList.toString();
        } else {    /* Page 1 CONTENT */
            Element contentElement = doc.getElementById("TT");
            String content = contentElement.html();
            tagList.append("<CONTENT>" + content + "</CONTENT>\n");
            Element createdDateElement = doc.selectFirst("body > div:nth-child(3) > div.thi > div > div.thi_left > div.thi_left_1 > dl > dt");
            String createdDate = createdDateElement.text();
            Pattern datetimePattern = Pattern.compile("\\d{4}年\\d{2}月\\d{2}日 \\d{2}:\\d{2}");
            Matcher datetimeMatcher = datetimePattern.matcher(createdDate);
            if (datetimeMatcher.find()) {
                createdDate = datetimeMatcher.group();
                createdDate = createdDate.replace("年", "-");
                createdDate = createdDate.replace("月", "-");
                createdDate = createdDate.replace("日", "");
                createdDate += ":00";
            }
            tagList.append("<CREATED_DATE>" + createdDate + "</CREATED_DATE>");
            htmlSrc = tagList.toString();
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
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
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
}
