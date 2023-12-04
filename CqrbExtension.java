package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 전제현
 * @version 1.1 (2023-10-19)
 * @title 충칭일보 수집 그룹 Extension (PXCQRB)
 * @since 2023-09-22
 */
public class CqrbExtension implements Extension {

    private static String SPLIT_CHARACTER = "!DQ!";
    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private String extensionName;
    private String cl_cd;
    private String origin_cd;
    private StringBuffer tagList;
    private boolean isTest;
    private JSONParser jsonParser;
    private Map<String, String> header;
//    private Set<String> checkUrlPatterns;

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
        // 첨부파일용 헤더 생성
//        imgHeader = new HashMap<>();
//        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
//        imgHeader.put("Cache-Control", "max-age=0");
        // 다운로드 허가할 url 추가 (해당 설정 하지 않으면 본문 내 모든 첨부파일 요소 다운로드 시도함)
//        checkUrlPatterns = new HashSet<>();
//        checkUrlPatterns.add(".*\\/wp-content\\/.*");
        jsonParser = new JSONParser();
        header = new HashMap<>();
        header.put("User-Agent", commonUtil.generateRandomUserAgent());
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        if (dqPageInfo.getParentUrl() == null & url.contains(".json")) {
            String[] tmpArr = url.split("\\?page=");
            if (tmpArr.length > 1) {
                url = tmpArr[0];
                String page = tmpArr[1];
                if (!"1".equals(page)) {
                    url = url.replace("index.json", "index_" + page + ".json");
                }
            }
        }

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        if (dqPageInfo.getParentUrl() != null) {    /* Page 1 LIST */
            return header;
        }

        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length()); // 초기화
        if (dqPageInfo.getParentUrl() == null && dqPageInfo.getUrl().contains(".json")) {   // PAGE 0 LIST
            try {
                JSONObject jsonObj = (JSONObject) jsonParser.parse(htmlSrc);
                JSONArray newslist = (JSONArray) jsonObj.get("newslist");
                for (int cnt = 0; cnt < newslist.size(); cnt++) {
                    JSONObject newsObj = (JSONObject) newslist.get(cnt);
                    String title = ((String) newsObj.get("title")).trim();
                    String titleUrl = ((String) newsObj.get("titleurl")).trim();
                    if (titleUrl.startsWith("/www/")) {
                        titleUrl = titleUrl.replace("/www/", "https://www.cqrb.cn/");
                    }
                    String newstime =  ((String) newsObj.get("newstime")).trim();
                    if (!"".equals(titleUrl)) {
                        tagList.append("<a href='" + titleUrl + "'>" + title + SPLIT_CHARACTER + newstime + "</a>\n");
                    }
                }
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        } else {   // PAGE 1 CONTENT
            String content = "";
            Document doc = Jsoup.parse(htmlSrc);
            Elements bodyElements = doc.getElementsByTag("body");
            content = bodyElements.html();
            tagList.append("<CONTENT>" + content + "</CONTENT>");
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
        int titleIdx = 0;
        int createdDateIdx = 0;
        String title = "";
        String createdDate = "";
        for (int i = 0; i < row.size(); i++) {
            String nodeId = row.getNodeByIdx(i).getId();
            String nodeName = row.getNodeByIdx(i).getName();
            String nodeValue = row.getNodeByIdx(i).getValue();
            if (nodeName.equals("title")) {
                titleIdx = i;
                String[] arr = nodeValue.split(SPLIT_CHARACTER);
                title = arr[0].trim();
                row.getNodeByIdx(i).setValue(title);
                createdDate = arr[1].trim();
                if (createdDateIdx != 0) {
                    row.getNodeByIdx(createdDateIdx).setValue(createdDate);
                }
            } else if (nodeName.equals("created_date")) {
                createdDateIdx = i;
                if (!"".equals(createdDate)) {
                    row.getNodeByIdx(i).setValue(createdDate);
                }
            } else if (nodeName.equals("source_class")) {
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
                connectionUtil.checkContentImage(row, dqPageInfo, null, "", documentId, cl_cd, origin_cd, "", header);  /* 이미지 처리 + 문서별 dqdoc 파일 저장 (User-Agent 변형 헤더 추가) */
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
