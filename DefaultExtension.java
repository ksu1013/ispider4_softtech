package extension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.commons.lang3.StringUtils;
import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.main.BbsMain;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.common.save.structure.RNode;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import extension.util.ChromeRemoteController;
import extension.util.PlaywrightController;

/**
 * 수집 Extension 부모 클래스
 *
 * @author 전제현
 * @version 1.7 (2025-09-01)
 * @since 2023-09-01
 */
public class DefaultExtension implements Extension, AddonExtension {

    protected static final int COLLECT_TYPE_FULL = 0;     /* 수집 타입: 전체 수집 */
    protected static final int COLLECT_TYPE_ADD = 1;      /* 수집 타입: 추가 수집 */
    protected static final int COLLECT_TYPE_REPLACE = 2;  /* 수집 타입: 갱신 수집 (갱신 수집은 안쓸거지만 일단 타입값은 가지고 있자...) */
    protected static final String COLLECT_PASS_CONTENT = "NO_COLLECT_DQDOC_ARTICLE";
    protected BbsMain bbsMain;
    protected BbsSetting bbsSetting;
    protected CommonUtil commonUtil;
    protected ConnectionUtil connectionUtil;
    protected LogUtil log;                              // ISPIDER4 로그 Util
    protected PlaywrightController playwrightController;
    protected ChromeRemoteController chromeRemoteController;
    protected Map<String, String> attachHeader;         // 첨부파일용 다운로드 헤더
    protected Set<String> dbAlarmCategoryListArr;
    protected Set<String> teamsAlarmCategoryListArr;
    protected Set<String> blankTitleUrlist;             // 제목(title)이 없는 주소 목록
    protected Set<String> blankContentUrlist;           // 내용(content)이 없는 주소 목록
    protected Set<String> blankCreatedDateUrlist;       // 작성일(created_date)이 없는 주소 목록
    protected Set<String> invalidCreatedDateUrlist;     // 작성일(created_date)이 잘못된 형식으로 입력된 주소 목록
    protected Set<String> checkUrlPatterns;             // 수집 허용 Url 패턴 목록 (해당 리스트에 값이 없을 경우 content 내 이미지 포함 첨부파일 다운로드 시도함)
    protected Set<String> prohibitUrlPatterns;          // 수집 제외 Url 패턴 목록
    protected Map<String, Map<String, Object>> crcCheckList;         // 중복 체크용 CRC 목록
    protected int collectType;                          // 수집 타입 (0: 전체수집 / 1: 추가수집 / 2: 갱신수집, 2번 갱신수집은 소프테크 프로젝트에선 사용하지 않음)
    protected int documentId;                           // 수집 시 documentId
    protected int attachDownloadDelayMin;
    protected int attachDownloadDelayMax;
    protected int titleIdx;
    protected int contentIdx;
    protected int createdDateIdx;
    protected int urlIdx;
    protected int videoIdx;
    protected int imagesIdx;
    protected int imagePathIdx;
    protected int connectContentPageCnt;    // Page 1 CONTENT 수집 시도 횟수, 이게 0건이면 Page 0 LIST에서 수집 목록 리스트를 제대로 생성하지 못했다는 의미이다.
    protected String hostName;
    protected String bbsName;
    protected String categoryName;
    protected String extensionName;
    protected String clCd;
    protected String originCd;
    protected String tsvListPageType;       // csv 파일 내용을 읽어서 리스트 목록을 가져오는 여부 체크하는 변수 추가
    protected String tsvListFileNumber;     // csv 파일을 수집 시 같은 출처 내의 다른 로직의 extension이 여럿 있을 경우 각 extension 수집 구준을 파일명에 주기 위한 변수 추가 (_1, _2 식으로 파일명에 추가하기 위함)
    protected StringBuffer tagList;
    protected String getListPageJsFilePath;
    protected String getContentPageJsFilePath;
    protected boolean isTest;   // 테스트 여부
    protected boolean isAttachDownloadDelay;   // 첨부파일 다운로드 시 시간 딜레이 부여 여부

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            hostName = localHost.getHostName();
        } catch (UnknownHostException e) {
            System.err.println("호스트 이름을 가져오는 중 오류 발생: " + e.getMessage());
        }
        String bbsId = dqPageInfo.getBbsId();
        Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
        bbsMain = Configuration.getInstance().getBbsMain(bbsId);
        String categoryId = bbsMain.getCategoryId();
        bbsSetting = Configuration.getInstance().getBbsSetting(bbsId);
        extensionName = bbsSetting.getExtensionName().replace("extension.", "");
        bbsName = bbsMain.getBbsName();
        categoryName = bbsMain.getCategoryName();
        log = new LogUtil();
        log.setBbsId(bbsId);
        log.setBbsName(bbsName);
        log.setCategoryId(categoryId);
        log.setCategoryName(categoryName);
        log.setCollectMode(dqPageInfo.getCollectType());
        System.out.println("Extension Execution Initiated: " + extensionName);
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil(reposit, dqPageInfo);
        playwrightController = new PlaywrightController(log, bbsMain);
        chromeRemoteController = new ChromeRemoteController(log, bbsMain, connectionUtil.isWindows());
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(reposit);
        blankTitleUrlist = new HashSet<>();
        blankContentUrlist = new HashSet<>();
        blankCreatedDateUrlist = new HashSet<>();
        invalidCreatedDateUrlist = new HashSet<>();
        // 첨부파일용 다운로드 헤더 초기화
        attachHeader = new HashMap<>();
//        attachHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
//        attachHeader.put("Cache-Control", "max-age=0");
        // 다운로드 허가할 url 추가 (해당 설정 하지 않으면 본문 내 모든 첨부파일 요소 다운로드 시도함)
        checkUrlPatterns = new HashSet<>();
//        checkUrlPatterns.add(".*\\/wp-content\\/.*");
        // 다운로드 제외할 url 추가
        prohibitUrlPatterns = new HashSet<>();
//        prohibitUrlPatterns.add(".*\\/wp-content\\/.*");
        isAttachDownloadDelay = true;
        attachDownloadDelayMin = 200;
        attachDownloadDelayMax = 500;
        collectType = dqPageInfo.getCollectType();
        // DB 알림 기록 및 teams 알림 설정
        String dbAlarmCategoryList = connectionUtil.getProperties("ALARM_CATEGORY_LIST");
        String teamsAlarmCategoryList = connectionUtil.getProperties("TEAMS_ALARM_CATEGORY_LIST");
        dbAlarmCategoryListArr = new HashSet<>(List.of(dbAlarmCategoryList.split(",")));
        teamsAlarmCategoryListArr = new HashSet<>(List.of(teamsAlarmCategoryList.split(",")));
        getListPageJsFilePath = System.getenv("ISPIDER4_HOME") + "/IspiderChromeRemote/default/getList.js";
        getContentPageJsFilePath = System.getenv("ISPIDER4_HOME") + "/IspiderChromeRemote/default/getContent.js";
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        if (dqPageInfo.getParentUrl() == null) {    /* Page 0 LIST */
            Map<String, String> params = commonUtil.getUrlQueryParams(url);
            if (params.containsKey("list")) {
                tsvListPageType = params.get("list");
                tsvListFileNumber = "";
            }
            if (params.containsKey("tsv_number")) {
                tsvListFileNumber = params.get("tsv_number");
            }
        }

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public Map<String, String> addAttachFileRequestHeader(String url) {
        return attachHeader;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length()); // 초기화
        if (dqPageInfo.getParentUrl() == null) { // Page 0
            // 수집대상 LIST, TARGET 테이블 적재
//            Map<String, RNode> assignNodes = bbsMain.getAssignNodes();
//            for (String key : assignNodes.keySet()) {
//                RNode rNode = assignNodes.get(key);
//                String value = rNode.getValue();
//                if (value.length() == 6 && value.chars().allMatch(Character::isDigit)) {
//                    String sourceId = value;
//                    String listUrl = dqPageInfo.getUrl();
//                    String listCrcId = commonUtil.stringToCrcId(sourceId + listUrl);
//                    if (!isTest || connectionUtil.isLocal())  {
//                        // IS_TARGET_LIST_HISTORY insert
//                        connectionUtil.upsertListHistory(listCrcId, sourceId, bbsName, listUrl);
//
//                        // Link 패턴 체크
//                        ISpiderBbsPage listPage = (ISpiderBbsPage) bbsMain.getBbsPage(0);
//                        List<Pattern> includePatterns = connectionUtil.compilePatterns(listPage.getLinkConf().getIncludeList());
//                        List<Pattern> excludePatterns = connectionUtil.compilePatterns(listPage.getLinkConf().getExcludeList());
//
//                        // 수집 URL
//                        Document document = Jsoup.parse(htmlSrc);
//                        List<String> pageUrls = new ArrayList<>();
//                        Elements pageLists = document.select("a[href]");
//
//                        for (Element pageList : pageLists) {
//                            String href = pageList.attr("href").trim();
//                            if (connectionUtil.shouldInclude(href, includePatterns, excludePatterns)) {
//                                pageUrls.add(href);
//                            }
//                        }
//
//                        // IS_TARGET_PAGE_HISTORY insert
//                        connectionUtil.upsertPageHistory(pageUrls, listCrcId, sourceId, bbsName);
//                    }
//                }
//            }
            log.info(bbsName + " Page 0 LIST 수집 URL 목록:\n" + htmlSrc);
        } else { // Page 0 LIST가 아닌 페이지들
            Map<String, RNode> assignNodes = bbsMain.getAssignNodes();
            String sourceId = "";
            for (String s : assignNodes.keySet()) {
                RNode rNode = assignNodes.get(s);
                String value = rNode.getValue();
                if (value.length() == 6 && value.chars().allMatch(Character::isDigit)) {
                    sourceId = value;
                }
            }

            Document document = Jsoup.parse(htmlSrc);
            Element headElement = document.selectFirst("head");
            Element bodyElement = document.selectFirst("body");     // body 엘리먼트 가져오기
            Elements headChildrenElements = headElement.children();    // body의 직계 자식 엘리먼트들 가져오기 (원래 수집 대상으로 만들어진 태그들)
            Elements bodyChildrenElements = bodyElement.children();    // body의 직계 자식 엘리먼트들 가져오기 (원래 수집 대상으로 만들어진 태그들)
            List<String> childTagList = new ArrayList<>();
            for (Element child : headChildrenElements) {
                String tagName = child.tagName();
                childTagList.add(tagName);
            }
            for (Element child : bodyChildrenElements) {
                String tagName = child.tagName();
                childTagList.add(tagName);
            }

            String titleHtml = "";
            String adHtml = "";

            if (childTagList.contains("content")
                && (childTagList.contains("created_date") || childTagList.contains("datetime"))) {
                for (String tagName : childTagList) {
                    Element tagElement = document.selectFirst(tagName);

                    if ("title".equalsIgnoreCase(tagName)) {
                        titleHtml = tagElement.text();
                    }

                    if ("CONTENT".equalsIgnoreCase(tagName)) {
                        // p 태그 내 class 요소 삭제
                        Elements pTagElements = tagElement.select("p");
                        if (!pTagElements.isEmpty()) {
                            pTagElements.removeAttr("class");
                        }
                        // CONTENT 태그 내 불필요한 태그 삭제
                        Elements removeElements = tagElement.select(
                                "svg"
                                + " , iframe"
                        );
                        if (!removeElements.isEmpty()) {
                            removeElements.remove();
                        }

                        // 광고 의심 태그 추출
                        Elements adElements = tagElement.select(
                                "[class*=advertise], [class*=advertisement], [class*=ad-], [class*=ads-], " +
                                        "[class*=sponsored], [class*=adverts], [class*=-ad], [class*=google], " +
                                        "[class*=promoted], [class*=related-], [class*=popular-posts], [class*=promoted], " +
                                        "[class*=advert], [class*=subscription], [class*=paywall]"
                        );

                        List<String> adTagSummaries = new ArrayList<>();
                        for (Element el : adElements) {
                            String summary = "<" + el.tagName();
                            if (el.hasAttr("class")) {
                                summary += " class=\"" + el.className() + "\"";
                            }
                            summary += ">";
                            adTagSummaries.add(summary);
                        }

                        adHtml = String.join("\n", adTagSummaries);

                    }
                    String value = tagElement.html();
                    tagList.append("<" + tagName.toUpperCase() + ">" + value + "</" + tagName.toUpperCase() + ">\n");
                }

                String listCrcId = commonUtil.stringToCrcId(sourceId + dqPageInfo.getUrl());

                int MAX_LEN = 500;

                if (!adHtml.isEmpty()) {
                    if (adHtml.length() > MAX_LEN) {
                        adHtml = adHtml.substring(0, MAX_LEN);
                    }
                    connectionUtil.upsertContentCheck(listCrcId, bbsName, dqPageInfo.getUrl(), sourceId, adHtml);
                }


            }
            if (connectionUtil.isLocal()) { // 로컬에서 돌릴땐 CONTENT 내용 수집 결과를 로그로 보기 위해 추가
                log.info("Page 1 CONTENT " + dqPageInfo.getUrl() + " 수집 결과:\n" + tagList.toString());
            }
            connectContentPageCnt++;
        }
        if (tagList.length() > 0) {
            htmlSrc = tagList.toString();
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
        documentId++;
        for (int idx = 0; idx < row.size(); idx++) {
            String nodeId = row.getNodeByIdx(idx).getId();
            String nodeName = row.getNodeByIdx(idx).getName();
            String nodeValue = row.getNodeByIdx(idx).getValue();
            if (nodeName.equals("title")) {
                titleIdx = idx;
            } else if (nodeName.equals("content")) {
                contentIdx = idx;
            } else if (nodeName.equals("created_date")) {
                createdDateIdx = idx;
            } else if (nodeName.equals("url")) {
                urlIdx = idx;
            } else if (nodeName.equals("source_class")) {
                clCd = nodeValue;
            } else if (nodeName.equals("source_ID")) {
                originCd = nodeValue;
            } else if (nodeName.equals("document_id")) {
                row.getNodeByIdx(idx).setValue(String.format("%06d", documentId));
            } else if (nodeName.equals("images")) {
                imagesIdx = idx;
            } else if (nodeName.equals("image_path")) {
                imagePathIdx = idx;
            } else if (nodeName.equals("video")) {
                videoIdx = idx;
            }
        }
    }

    @Override
    public boolean validData(Row row, DqPageInfo dqPageInfo) {
        boolean isCheck = true;
        String title = "";
        String content = "";
        String createdDate = "";
        String url = "";
        String sourceId = "";
        String documentIdStr = String.format("%06d", documentId);
        try {
            for (int idx = 0; idx < row.size(); idx++) {
                String nodeName = row.getNodeByIdx(idx).getName();
                String nodeValue = row.getNodeByIdx(idx).getValue();
                if (nodeName.equals("title")) {
                    title = nodeValue;
                    if (title.contains("^DQ")) {    // 2025-08-20 jhjeon: ^DQ가 제목에 있을 경우 ^DQ 앞의 텍스트만 타이틀로 입력한다.
                        title = title.split("\\^DQ")[0];
                    }
                } else if (nodeName.equals("content")) {
                    content = nodeValue.replaceAll("\\^DQ","");
                } else if (nodeName.equals("created_date")) {
                    createdDateIdx = idx;
                    createdDate = nodeValue;
                } else if (nodeName.equals("url")) {
                    url = nodeValue;
                } else if (nodeName.equals("source_ID")) {
                    sourceId = nodeValue;
                }
            }

            if (url.contains("127.0.0.1") || url.contains("localhost")) {
                log.info("[URL: " + url + " Dqdoc Collect Excluded] 현재 수집하는 페이지의 url이 " +
                        "정상적이지 않아 수집을 중단합니다.");
                return false;
            }

            // SOURCE_ID + TITLE 중복 체크용 CRC 생성 및 비교 로직
            crcCheckList = connectionUtil.selectColHistory(sourceId);
            String crcId = commonUtil.stringToCrcId(sourceId + title);
            if (crcCheckList.containsKey(crcId)) { // 수집 중복 여부 체크
                Map<String, Object> crcCheckObj = crcCheckList.get(crcId);
                String bbsName = (String) crcCheckObj.get("BBS_NAME");
                if (!this.bbsName.equalsIgnoreCase(bbsName)) {
                    log.info("[URL: " + url + " Dqdoc Collect Excluded] 현재 수집하는 게시판명이 " + this.bbsName + "이고, " +
                            "해당 수집 데이터는 " + bbsName + " 게시판에서 수집했으므로 중복 수집 방지를 위해 수집을 중단합니다.");
                    return false;
                } else if (collectType != COLLECT_TYPE_FULL) {
                    log.info("[URL: " + url + " Dqdoc Collect Excluded] 이미 수집한 데이터를 추가수집했으므로 해당 문서의 수집을 중단합니다.");
                    return false;
                }   // 이미 수집한 게시판에서 전체수집으로 다시 수집하는 경우에는 재수집을 허용한다.
            }

            if (StringUtils.isBlank(title)
                    || StringUtils.isBlank(content)
                    || (!StringUtils.isBlank(createdDate) && !commonUtil.isValidDateFormat(createdDate))) {
                isCheck = false;
                connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
                if (StringUtils.equals(title, "")) {
                    blankTitleUrlist.add(url);
                    log.warn("[URL: " + url + " Dqdoc Collect Fail] 제목(TITLE)이 누락되어 DQDOC 파일 생성을 중단합니다.");
                }
                if (StringUtils.equals(content, "")) {
                    blankContentUrlist.add(url);
                    log.warn("[URL: " + url + " Dqdoc Collect Fail] 내용(CONTENT)이 누락되어 DQDOC 파일 생성을 중단합니다.");
                }
                if (!StringUtils.equals(createdDate, "") && !commonUtil.isValidDateFormat(createdDate)) {
                    invalidCreatedDateUrlist.add(url);
                    log.setUrl(url);
                    log.warn("[URL: " + url + " Dqdoc Collect Fail] 작성일(CREATED_DATE) 날짜 형식이 잘못되어 DQDOC 파일 생성을 중단합니다. 수집된 작성일 값: " + createdDate);
                }
            } else if (StringUtils.equals(COLLECT_PASS_CONTENT, content)) { /* 2024-04-02 jhjeon: 특정 오류 발생 안시키고 본문이 없어서 걸러야 할 페이지를 구분할 때 사용하는 조간 부분 추가 */
                isCheck = false;
                log.warn("[URL: " + url + " Dqdoc Collect Excluded] 본문에 내용(CONTENT)이 없어서 해당 페이지는 수집되지 않았습니다.");
            } else {
                if (StringUtils.equals(createdDate, "")) {
                    blankCreatedDateUrlist.add(url);
                } else {    /* 2024-05-13 jhjeon: 작성일(created_date)이 수집일(collected_date)보다 미래일 경우의 조치 로직 추가 */
                    LocalDateTime createdDateLdt = LocalDateTime.parse(createdDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));    // 주어진 문자열을 LocalDateTime 객체로 변환
                    LocalDateTime currentDateTime = LocalDateTime.now();    // 현재 시간을 LocalDateTime 객체로 얻기
                    if (currentDateTime.isBefore(createdDateLdt)) {  /* 이벤트 날짜가 현재 날짜보다 미래면 작성일을 수집일(collected_date)로 저장하도록 빈값으로 설정한다. */
                        blankCreatedDateUrlist.add(url);
                        createdDate = "1997-01-01 00:00:00";
                        row.getNodeByIdx(createdDateIdx).setValue(createdDate); // 작성일이 빈값일 경우 수집일(collected_date)을 작성일(created_date)로 설정하는 로직은 checkContentImage 함수 안에 있으므로 여기서는 created_date 값을 빈값으로 입력하는 것까지만 처리한다.
                    }
                }
                connectionUtil.saveCollectFiles(row, dqPageInfo, documentIdStr,
                        clCd, originCd, attachHeader, checkUrlPatterns,
                        prohibitUrlPatterns, isAttachDownloadDelay,
                        attachDownloadDelayMax, attachDownloadDelayMin);  // 첨부파일 다운로드 및 문서별 DQDOC 파일 저장
                Map<String, Object> rowObj;
                if (!isTest || connectionUtil.isLocal()) {  // 운영일 경우 테스트 수집 때 수집 기록을 남기지 않는다. 로컬은 테스트할 때 insert 및 update 확인을 해야하니 그냥 실행...
                    if (crcCheckList.containsKey(crcId)) {    // 수집 데이터의 CRC 값이 이미 있다면 update한다.
                        rowObj = connectionUtil.updateColHistory(crcId, sourceId, bbsName, url, content.length());
                    } else {    // 수집 데이터의 CRC 값이 기존에 없었다면 insert한다.
                        rowObj = connectionUtil.insertColHistory(crcId, sourceId, bbsName, url, content.length());
                    }
                    crcCheckList.put(crcId, rowObj);
                }
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
            log.error("■■■ " + extensionName + " validData 함수 Exception 발생!!! ■■■", e);
        }

        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        chromeRemoteController.closeChrome();

        if (teamsAlarmCategoryListArr.contains(categoryName) && connectContentPageCnt == 0) {    // teams 실시간 알림을 보내야 하는 경우
            sendTeamsAlarm();
        }

        if (dbAlarmCategoryListArr.contains(categoryName)) {    // 주기적으로 전달해야 할 team 알림용 db 데이터 갱신하는 경우
            String statusCheck = connectionUtil.selectColWarrHistory(bbsName);
            if (connectContentPageCnt == 0) {
                String status = "F";
                if ("S".equalsIgnoreCase(statusCheck)) {
                    connectionUtil.updateColWarrHistory(hostName, categoryName, bbsName, status);
                } else if (statusCheck == null || "".equalsIgnoreCase(statusCheck)) {
                    connectionUtil.insertColWarrHistory(hostName, categoryName, bbsName, status);
                }
            } else {
                String status = "S";
                if ("F".equalsIgnoreCase(statusCheck)) {
                    connectionUtil.updateColWarrHistory(hostName, categoryName, bbsName, status);
                } else if (statusCheck == null || "".equalsIgnoreCase(statusCheck)) {
                    connectionUtil.insertColWarrHistory(hostName, categoryName, bbsName, status);
                }
            }
        }

        try {
            if (!connectionUtil.isLocal() && !isTest) { /* 로컬 및 테스트 환경이 아니면 로그를 남긴다. */
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), clCd, originCd);  // 수집로그 DB 저장은 GCP 운영서버에서만 수행
            }
            // 2025-05-21 jhjeon: 문서별 첨부파일명 변경 및 이동, DQDOC 파일 이동 처리 로직 함수 통합 및 수정
            connectionUtil.moveCollectFiles(dqPageInfo.getBbsId(), true, false);    // 수집된 파일 처리기 쪽 폴더로 이동
            connectionUtil.printFilesInfo();    // 수집된 첨부파일 목록 출력
            connectionUtil.printCollectLog(dqPageInfo.getBbsId(), clCd, originCd);  // 수집로그 출력
        } catch (Exception e) {
            log.error("■■■ endExtension Exception 발생!!!! ■■■", e);
        } finally {
            if (!blankTitleUrlist.isEmpty()) {
                log.warn("제목(title)이 없는 페이지 목록(blankTitleUrlist): " + blankTitleUrlist);
            }
            if (!blankContentUrlist.isEmpty()) {
                log.warn("내용(content)이 없는 페이지 목록(blankContentUrlist): " + blankContentUrlist);
            }
            if (!blankCreatedDateUrlist.isEmpty()) {
                log.warn("작성일(created_date)이 없는 페이지 목록(blankCreatedDateUrlist): " + blankCreatedDateUrlist);
            }
            if (!invalidCreatedDateUrlist.isEmpty()) {
                log.warn("작성일(created_date)이 잘못된 형식으로 입력된 페이지 목록(invalidCreatedDateUrlist): " + invalidCreatedDateUrlist);
            }
            log.info("Extension Execution End: " + extensionName);
        }
    }

    /**
     * 수집 작업 완료 또는 오류 발생 시 Microsoft Teams를 통해 알림을 보냅니다.
     */
    public void sendTeamsAlarm() {
        String warningAlarmTitle = hostName + " " + categoryName + " " + bbsName + " ISSUE CHECK";
        String warningAlarmSummary = hostName + " " + categoryName + " " + bbsName + " COLLECT ISSUE CHECKED";
        String warningAlarmMessage = hostName + " " + categoryName + " " + bbsName + " Collect Issue Check PLZ!";
        JSONObject messageObj = new JSONObject();
        messageObj.put("title", warningAlarmTitle);
        messageObj.put("summary", warningAlarmSummary);
        messageObj.put("message", warningAlarmMessage);
        String apiUrl = "http://10.100.0.2:8002/api/alarm/send";
        if (connectionUtil.isLocal()) {
            apiUrl = "http://34.64.85.109:8002/api/alarm/send";
        }
        HttpURLConnection connection = null;
        try {
            // URL 객체 생성
            URL url = new URL(apiUrl);

            // HttpURLConnection 객체 생성 및 연결 설정
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true); // OutputStream으로 데이터 쓰기 허용
            connection.setRequestProperty("Content-Type", "application/json"); // JSON 형식 명시
            connection.setConnectTimeout(1000); // 연결 타임아웃 1초
            connection.setReadTimeout(1000); // 읽기 타임아웃 1초

            // RequestBody 생성 및 전송
            String requestBody = messageObj.toJSONString();
            byte[] out = requestBody.getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(out);
            }

            // 응답 코드 확인 (응답 본문은 무시)
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("API 호출 성공!");
            } else {
                System.err.println("API 호출 실패. 응답 코드: " + responseCode);
            }
        } catch (IOException e) {
            System.err.println("수집 오류 알림 API 호출 중 예외 발생: " + e);
        } finally {
            // 4. 연결 종료
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
