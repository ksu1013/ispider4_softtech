package extension;

import java.io.*;
import java.net.*;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import com.diquest.ispider.common.logdata.CommonLogData;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class CommonUtil {

    private static final Map<String, Integer> OS_WEIGHTS = new HashMap<>();
    private static final Map<String, Integer> BROWSER_WEIGHTS = new HashMap<>();
    private static final Map<String, Integer> VERSION_WEIGHTS = new HashMap<>();
    private static final String LIST_START_TAG = "<!--List Start-->";
    private static final String LIST_END_TAG = "\n<!--List End-->";
    private static final String ispider4Home = System.getenv("ISPIDER4_HOME");
    private DqPageInfo dqPageInfo;
    private CommonLogData log;

    static {
        // OS 가중치 설정
        OS_WEIGHTS.put("Windows NT 10.0", 5);
        OS_WEIGHTS.put("Windows NT 6.3", 4);
        OS_WEIGHTS.put("Windows NT 6.2", 3);
        OS_WEIGHTS.put("Windows NT 6.1", 3);
        OS_WEIGHTS.put("Windows NT 6.0", 2);
        OS_WEIGHTS.put("Windows NT 5.1", 2);
        OS_WEIGHTS.put("Windows NT 5.0", 1);
        OS_WEIGHTS.put("Macintosh; Intel Mac OS X 10_15_7", 5);
        OS_WEIGHTS.put("Macintosh; Intel Mac OS X 10_14_6", 4);
        OS_WEIGHTS.put("Macintosh; Intel Mac OS X 10_13_6", 3);
        OS_WEIGHTS.put("Macintosh; Intel Mac OS X 10_12_6", 2);
        OS_WEIGHTS.put("Macintosh; Intel Mac OS X 10_11_6", 2);
        OS_WEIGHTS.put("Macintosh; Intel Mac OS X 10_10_5", 1);
        OS_WEIGHTS.put("Macintosh; Intel Mac OS X 10_9_5", 1);
        OS_WEIGHTS.put("X11; Linux x86_64", 3);
        OS_WEIGHTS.put("X11; Ubuntu; Linux x86_64", 2);

        // 브라우저 가중치 설정
        BROWSER_WEIGHTS.put("Chrome", 5);
        BROWSER_WEIGHTS.put("Firefox", 4);
        BROWSER_WEIGHTS.put("Safari", 3);
        BROWSER_WEIGHTS.put("Opera", 2);
        BROWSER_WEIGHTS.put("Internet Explorer", 1);

        // 버전 가중치 설정
        VERSION_WEIGHTS.put("75.0.3770.100", 5);
        VERSION_WEIGHTS.put("85.0.4183.121", 4);
        VERSION_WEIGHTS.put("81.0.4044.129", 3);
        VERSION_WEIGHTS.put("77.0.3865.90", 2);
        VERSION_WEIGHTS.put("72.0.3626.81", 1);
    }

    public CommonUtil() {}

    public CommonUtil(DqPageInfo dqPageInfo) {
        init(dqPageInfo);
    }

    public void init(DqPageInfo dqPageInfo) {
        this.dqPageInfo = dqPageInfo;
        this.log = new CommonLogData();
        this.log.setBbsId(dqPageInfo.getBbsId());
        this.log.setBbsName(dqPageInfo.getBbsName());
        if (dqPageInfo.getCategoryId() != null) {
            this.log.setCategoryId(dqPageInfo.getCategoryId());
            this.log.setCategoryName(dqPageInfo.getCategoryName());
        }
    }

    /**
     * 주어진 문자열이 유효한 숫자 형식으로 변환될 수 있는지 체크합니다.
     * 정수, 소수점, 지수 표기법(예: "123", "-4.5", "1.2e3")을 포함하는 문자열을 숫자로 간주합니다.
     *
     * <p><b>특정 케이스 처리:</b></p>
     * <ul>
     * <li>입력 문자열이 null이거나 앞뒤 공백 제거 후 빈 문자열인 경우 false를 반환합니다.</li>
     * <li>유효한 숫자 형식으로 파싱할 수 없는 문자열은 false를 반환합니다.</li>
     * </ul>
     *
     * @param s 숫자 형식을 체크할 대상 문자열.
     * @return 문자열이 유효한 숫자 형식으로 파싱될 수 있으면 true, 그렇지 않으면 false를 반환합니다.
     */
    public boolean isNumeric(String s) {
        // 1. null 또는 빈 문자열 체크 (숫자가 아님)
        if (s == null || s.trim().isEmpty()) {
            return false;
        }

        // 2. 숫자로 파싱 가능한지 시도
        try {
            Double.parseDouble(s); // Double로 파싱 시도 (정수, 소수점, 지수 표기법 등 대부분의 숫자 형식 처리)
            return true; // 예외가 발생하지 않으면 유효한 숫자 형식임
        } catch (NumberFormatException e) {
            // NumberFormatException이 발생하면 숫자로 파싱할 수 없는 문자열임
            return false;
        }
        // Note: Character.isDigit()이나 String의 matches()와 복잡한 정규식을 사용할 수도 있으나,
        // Double.parseDouble() 방식이 가장 범용적이고 정확합니다.
    }

    /**
     * 주어진 문자열에서 숫자(0-9)만 추출하여 새로운 문자열로 반환합니다.
     * 숫자 이외의 모든 문자는 결과 문자열에서 제거됩니다.
     *
     * <p><b>특정 케이스 처리:</b></p>
     * <ul>
     * <li>입력 문자열 {@code text}가 null이거나 빈 문자열("")인 경우 null을 반환합니다.</li>
     * <li>입력 문자열에 숫자가 전혀 포함되어 있지 않은 경우 빈 문자열("")을 반환합니다.</li>
     * </ul>
     *
     * @param text 숫자를 추출할 대상 원본 문자열. null 또는 빈 문자열이 될 수 있습니다.
     * @return 추출된 숫자들로만 구성된 문자열. 입력이 null 또는 빈 문자열인 경우 null을 반환하며, 숫자가 하나도 없으면 빈 문자열("")을 반환합니다.
     */
    public String getNumberByText(String text) {
        // null 또는 빈 문자열("") 입력 시 null 반환
        // text.equals("") 대신 text.isEmpty() 사용 (동일 기능)
        if (text == null || text.isEmpty()) {
            return null;
        }

        // 숫자(0-9)가 아닌 모든 문자를 제거하여 숫자만 남김
        return text.replaceAll("[^0-9]", "");
    }

    /**
     * String to json
     *
     * @param jsonText
     * @return
     */
    public JsonObject stirngToJson(String jsonText) {
        if (jsonText == null || jsonText.equals("")) {
            System.out.println("[NOT FOUND]jsonText is null..");
            return null;
        }
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(jsonText).getAsJsonObject();

        return obj;
    }

    /**
     * 주어진 문자열(text) 내에서 특정 시작 태그({@code startTag})와 종료 태그({@code endTag}) 사이에 있는
     * 부분 문자열을 추출하여 반환합니다.
     *
     * <p><b>동작 방식:</b></p>
     * <ul>
     * <li>원본 문자열({@code text})에서 {@code startTag}의 첫 번째 출현 위치를 찾습니다.</li>
     * <li>{@code startTag}의 끝 바로 다음 문자부터 검색을 시작하여 {@code endTag}의 첫 번째 출현 위치를 찾습니다.</li>
     * <li>{@code startTag} 바로 다음부터 {@code endTag} 바로 이전까지의 문자열을 추출합니다.</li>
     * </ul>
     *
     * <p><b>특정 케이스 처리:</b></p>
     * <ul>
     * <li>{@code text}가 {@code startTag} 또는 {@code endTag} 중 하나라도 포함하지 않는 경우 빈 문자열("")을 반환합니다.</li>
     * <li>{@code startTag}는 존재하지만, 그 뒤에 {@code endTag}가 존재하지 않는 경우 (또는 {@code endTag}가 {@code startTag}보다 먼저 나타나는 경우), {@code try-catch} 블록에 의해 예외가 처리되며 빈 문자열("")이 반환됩니다.</li>
     * <li>{@code startTag} 또는 {@code endTag}가 여러 번 나타나는 경우, 첫 번째로 발견된 태그를 기준으로 동작합니다.</li>
     * <li>추출 중 예외 발생 시 상세 오류 내용은 {@code e.printStackTrace()}에 의해 콘솔(표준 오류 스트림)에 출력됩니다.</li>
     * <li>입력 {@code text}, {@code startTag}, {@code endTag}가 null인 경우의 동작은 현재 코드로는 예외가 발생할 수 있으니 주의해야 합니다. (메소드 초반에 null 체크 로직 추가 권장)</li>
     * </ul>
     *
     * @param startTag 추출할 부분 문자열의 시작을 나타내는 문자열 (결과에는 포함되지 않음). null은 예상치 못한 동작을 유발할 수 있습니다.
     * @param endTag 추출할 부분 문자열의 끝을 나타내는 문자열 (결과에는 포함되지 않음). null은 예상치 못한 동작을 유발할 수 있습니다.
     * @param text 부분 문자열을 검색할 대상 원본 문자열. null은 예상치 못한 동작을 유발할 수 있습니다.
     * @return 시작 태그와 종료 태그 사이에 있는 부분 문자열. 태그가 없거나 추출 중 오류가 발생한 경우 빈 문자열("")을 반환합니다.
     */
    public String getSubStringResult(String startTag, String endTag, String text) {
        if (text == null || startTag == null || endTag == null || startTag.isEmpty() || endTag.isEmpty()) {
            return ""; // 유효하지 않은 입력 처리
        }
        int startIndex = text.indexOf(startTag);
        if (startIndex == -1) {
            return ""; // 시작 태그 없음
        }
        int contentStartIndex = startIndex + startTag.length();
        // 시작 태그 바로 다음부터 검색하여 종료 태그 찾기
        int endIndex = text.indexOf(endTag, contentStartIndex);
        if (endIndex == -1) {
            return ""; // 시작 태그 뒤에 종료 태그 없음
        }
        return text.substring(contentStartIndex, endIndex);
    }

    /**
     * 주어진 URL 문자열에서 프로토콜, 포트, 경로, 쿼리 파라미터, 프래그먼트 등을 제외한
     * **순수 도메인(호스트 이름) 부분만 추출하여 반환**합니다.
     *
     * <p>예시:</p>
     * <ul>
     * <li>"http://naver.com/kr"       => "naver.com"</li>
     * <li>"https://www.google.com/"     => "www.google.com"</li>
     * <li>"ftp://user:pass@ftp.example.com:21/file" => "ftp.example.com"</li>
     * <li>"http://localhost:8080/path"  => "localhost"</li>
     * <li>"example.com"               => "example.com" (프로토콜이 없는 경우)</li>
     * </ul>
     *
     * <p><b>특정 케이스 처리:</b></p>
     * <ul>
     * <li>입력 URL이 null 또는 빈 문자열인 경우: null 또는 빈 문자열을 반환합니다.</li> // 실제 함수 로직에 따라 수정
     * <li>유효하지 않은 형식의 URL인 경우: null 또는 빈 문자열을 반환하거나 IllegalArgumentException 등을 발생시킬 수 있습니다.</li> // 실제 함수 로직에 따라 수정
     * <li>주소에 경로 구분자 '/'가 없는 경우 (예: "http://example.com"): 도메인 부분만 정확히 추출합니다.</li> // 수정 이력에 대한 상세 설명
     * </ul>
     *
     * @param url 도메인 부분을 추출할 전체 URL 문자열. null 또는 빈 문자열이 될 수 있습니다.
     * @return 추출된 도메인(호스트 이름) 문자열. 추출에 실패하거나 입력이 유효하지 않은 경우 null 또는 빈 문자열을 반환할 수 있습니다.
     */
    public String getDomain(String url) {
        String preText = "http://";
        if (url.contains("https://")) {
            preText = "https://";
        }
        String newUrl = url.substring(url.indexOf(preText) + preText.length());
        String newUrl2 = "";
        if (newUrl.contains("/")) {
            newUrl2 = newUrl.substring(0, newUrl.indexOf("/"));
        } else if (newUrl.contains("?")) {
            newUrl2 = newUrl.substring(0, newUrl.indexOf("?"));
        } else {
            newUrl2 = newUrl;
        }

        return preText + newUrl2;
    }

    /**
     * 주어진 URL 문자열에서 프로토콜(http:// 또는 https://로 가정)을 포함한 도메인 및 경로 부분을
     * 추출하려 시도하며, 첫 번째 쿼리 파라미터 구분자('?') 이전까지의 문자열을 반환합니다.
     *
     * <p><b>주의:</b> 이 함수는 매우 제한적인 형식의 URL만 처리할 수 있으며,
     * 다음과 같은 경우 {@code IndexOutOfBoundsException} 등의 예외가 발생하여
     * 프로그램이 비정상 종료될 수 있습니다:</p>
     * <ul>
     * <li>URL이 "http://" 또는 "https://"로 시작하지 않는 경우.</li>
     * <li>URL에 쿼리 파라미터 구분자('?')가 포함되지 않은 경우.</li>
     * <li>입력 문자열이 null인 경우.</li>
     * <li>URL 형식이 예상과 다른 복잡한 형태인 경우.</li>
     * </ul>
     *
     * @param url 처리할 URL 문자열. 일반적으로 "http://" 또는 "https://"로 시작하고 '?' 문자를 포함하는 형태를 예상합니다.
     * @return 프로토콜을 포함하여 첫 번째 '?' 문자 이전까지의 URL 문자열 부분.
     * @throws IndexOutOfBoundsException 주어진 URL 형식이 예상과 다르거나 필수 문자가 없는 경우 발생할 수 있습니다.
     * @see java.net.URI
     * @see java.net.URL
     */
    public String getFullDomain(String url) {
        String preText = "http://";
        if (url.contains("https://")) {
            preText = "https://";
        }
        String newUrl = url.substring(url.indexOf(preText) + preText.length());
        String newUrl2 = newUrl.substring(0, newUrl.indexOf("?"));
        return preText + newUrl2;
    }

    /**
     * 추가적인 HTML 정보를 받아 오기 위한 HTTP CONNECTION
     *
     * @param url 접속 페이지 URL
     * @param encoding 페이지 접속 시 인코딩 설정
     * @param connectionUtil ConnectionUtil 변수, 프록시 사용 여부를 체크하기 위해 매개변수로 받아 사용한다.
     * @return
     * */
    public String getProductDetailPage(String url, String encoding, ConnectionUtil connectionUtil) {
        StringBuffer detail = new StringBuffer();
        try {
            URL targetURL = new URL(url);
            URLConnection urlConn = null;
            if (connectionUtil.isProxy()) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(connectionUtil.getProxyIp(), connectionUtil.getProxyPortNumber()));
                urlConn = targetURL.openConnection(proxy);
            } else {
                urlConn = targetURL.openConnection();
            }
            HttpURLConnection hurlc = (HttpURLConnection) urlConn;
            hurlc.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36");
            hurlc.setRequestProperty("Referer", url);
            hurlc.setRequestMethod("GET");
            hurlc.setDoOutput(true);
            hurlc.setDoInput(true);
            hurlc.setUseCaches(false);
            hurlc.setDefaultUseCaches(false);
            hurlc.setReadTimeout(20000);
            hurlc.setConnectTimeout(20000);

            BufferedReader in = new BufferedReader(new InputStreamReader(hurlc.getInputStream(), encoding));
            for (String line = null; (line = in.readLine()) != null; ) {
                detail.append(line + "\r\n");
            }
            in.close();
            if (hurlc != null) hurlc.disconnect();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return detail.toString();
    }

    /**
     * TODO pickUpDate 주석 작성 필요
     *
     * @param input
     * @return
     * */
    public String pickUpDate(String input) {
        String value = "";
        String ParsingData = "";
        if (!(input.equals("") && !(input.equals(null)))) {
            Pattern DatePattern = Pattern
                    .compile("[0-9]{4}[- /. /년 ]*(0[1-9]|1[0-2]|[1-9])[- /. /월]*(0[1-9]|[1-2][0-9]|3[0-1]|[1-9])");
            Matcher DateMatcher = DatePattern.matcher(input.replace(" ", ""));
            if (DateMatcher.find()) {
                value = DateMatcher.group();
            } else {
                value = "1900-01-01";
            }
            value = value.replace(".", "-").replace("년", "-").replace("월", "-").replace("/", "-");
            try {
                SimpleDateFormat DateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date ParsingDateData = DateFormat.parse(value);
                ParsingData = DateFormat.format(ParsingDateData);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ParsingData = ParsingData.replace("-", "");
        }
        return ParsingData;
    }

    /**
     * TODO removeWhiteSpace 주석 작성 필요
     * 공백, 탭 , 개행 등 제거
     *
     * @param input
     * @return
     * */
    public String removeWhiteSpace(String input) {
        if (input == null || input.equals("")) {
            return input;
        }
        return input.replaceAll("(\r\n|\r|\n|\n\r|\\p{Z}|\\t)", "");
    }

    /**
     * TODO getProductDetailPageParam 주석 작성 필요
     * 추가적인 HTML 정보를 받아 오기 위한 http Connection
     * 1페이지 접속은 되나 페이지 변경시 안될때 사용
     * url , page파라미터(ex: pageNo=2), encoding Type
     *
     * @param url
     * @param pageParam
     * @param encoding
     * @return 
     * */
    public String getProductDetailPageParam(String url, String pageParam, String encoding) {
        StringBuffer detail = new StringBuffer();
        try {
            URL targetURL = new URL(url);
            URLConnection urlConn = targetURL.openConnection();
            HttpURLConnection hurlc = (HttpURLConnection) urlConn;
            hurlc.setRequestProperty("user-agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36");
            hurlc.setRequestProperty("Referer", url);
            hurlc.setRequestProperty("Upgrade-Insecure-Requests", "1");
            hurlc.setRequestMethod("POST");
            hurlc.setRequestProperty("x-requested-with", "XMLHttpRequest");
            hurlc.setRequestProperty("accept-language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            hurlc.setRequestProperty("content-type", "application/x-www-form-urlencoded; charset=" + encoding);
            hurlc.setDoOutput(true);
            hurlc.setDoInput(true);
            hurlc.setUseCaches(false);
            hurlc.setDefaultUseCaches(false);
            hurlc.setReadTimeout(30000);
            hurlc.setConnectTimeout(30000);
            OutputStreamWriter wr = new OutputStreamWriter(hurlc.getOutputStream());
            wr.write(pageParam);
            wr.flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(hurlc.getInputStream(), encoding));
            for (String line = null; (line = in.readLine()) != null; )
                detail.append(line + "\n");

            in.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return detail.toString();
    }

    /**
     * TODO changeDateFormat 주석 작성 필요
     * created_date 날짜 포맷 변경
     *
     * @param datetimeHtml
     * @return
     * */
    public String changeDateFormat(String datetimeHtml) {
        String datetime = "";
        if (datetimeHtml != null && !datetimeHtml.equals("")) {
            LocalDateTime localtime = LocalDateTime.from(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(datetimeHtml)).atZone(ZoneOffset.UTC));
            datetime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(localtime);
        }

        return datetime;
    }

    /**
     * TODO getUrlList 주석 작성 필요
     * 0뎁스 LIST 만들기 (Jsoup Elements)
     *
     * @param urlList
     * @param domain
     * @return
     * */
    public String getUrlList(Elements urlList, String domain) {
        StringBuffer tagList = new StringBuffer();
        urlList = urlList.select("a[href]");
        tagList.append(LIST_START_TAG);
        for (Element link : urlList) {
            String currentUrl = link.attr("href");
            if (currentUrl.contains(domain)) {
                tagList.append("\n<a href=\"" + currentUrl + "\">link</a>");
            } else {
                tagList.append("\n<a href=\"" + domain + currentUrl + "\">link</a>");
            }
        }
        tagList.append(LIST_END_TAG);

        return tagList.toString();
    }

    public String getUrlLists(Elements urlList, String domain) {
        StringBuffer tagList = new StringBuffer();
        urlList = urlList.select("a[href]");
        tagList.append(LIST_START_TAG);
        for (Element link : urlList) {
            String currentUrl = link.attr("href");
            if (currentUrl.contains(domain) || currentUrl.contains("https") || currentUrl.contains("http")) {
                tagList.append("\n<a href=\"" + currentUrl + "\">link</a>");
            } else {
                tagList.append("\n<a href=\"" + domain + currentUrl + "\">link</a>");
            }
        }
        tagList.append(LIST_END_TAG);

        return tagList.toString();
    }

    // 0뎁스 LIST 만들기 (Set<String>)
    public String getUrlList(Set<String> urlList, String domain) {
        StringBuffer tagList = new StringBuffer();
        tagList.append(LIST_START_TAG);
        for (String link : urlList) {
            if (link.contains(domain)) {
                tagList.append("\n<a href=\"" + link + "\">link</a>");
            } else {
                tagList.append("\n<a href=\"" + domain + link + "\">link</a>");
            }
        }
        tagList.append(LIST_END_TAG);

        return tagList.toString();
    }

    // 1뎁스 tag 만들기  title, create_date, content
    public String makeCollectContext(String title, String datetime, String content) {
        return makeCollectContext(title, datetime, content, null);
    }

    // 1뎁스 tag 만들기  title, create_date, content, url
    public String makeCollectContext(String title, String datetime, String content, String url) {
        StringBuffer tagList = new StringBuffer();
        tagList.append("<TITLE>" + title + "</TITLE>\n");
        tagList.append("<DATETIME>" + datetime + "</DATETIME>\n");
        tagList.append("<CONTENT>" + content + "</CONTENT>\n");
        if (url != null && !"".equals(url)) {
            tagList.append("<URL>" + url + "</URL>");
        }

        return tagList.toString();
    }

    // img 태그에 src의 뒤에 파라미터 잡것을 때는 메소드
    public String removeImgParam(String content) {
        if (content.contains("<img")) {
            Pattern pt = Pattern.compile("src=\"(.*?)\"");
            Matcher mt = pt.matcher(content);
            while (mt.find()) {
                if (mt.group(1).contains("?") && !mt.group(1).contains("/?")) {
                    String cutUrl = mt.group(1).substring(0, mt.group(1).indexOf("?"));
                    content = content.replace(mt.group(1), cutUrl);
                }
            }
        }
        return content;
    }

    /**
     * TODO makeCollectContext 주석 작성 필요
     * 1, 2depth Tag String 만드는 함수
     * 기존 makeCollectContext 함수를 참고해서 만듬
     *
     * @param tagMap 각 태그를 저장한 HashMap 변수
     * @return
     * */
    public String makeCollectContext(Map<String, String> tagMap) {
        String tagList = "";
        String startTag = "";
        String endTag = "";
        if (tagMap.containsKey("STARTTAG") && tagMap.containsKey("ENDTAG")) {
            startTag = tagMap.get("STARTTAG");
            endTag = tagMap.get("ENDTAG");
            tagList = startTag + "\n";
        }
        for (String key : tagMap.keySet()) {
            String value = tagMap.get(key);
            tagList = "<" + key + ">" + value + "</" + key + ">\n";
        }
        if (tagMap.containsKey("STARTTAG") && tagMap.containsKey("ENDTAG")) {
            tagList += endTag;
        }

        return tagList.toString();
    }

    /**
     * url get 파라마터를 HashMap에 담는 함수
     *
     * @param url 파라미터를 추려낼 url 주소
     * @return url parameter hashmap 변수
     */
    public Map<String, String> getUrlQueryParams(String url) {

        Map<String, String> paramMap = new HashMap<>();
        URL dummyUrl = null;
        try {
            dummyUrl = new URL(url);
            String query = dummyUrl.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] keyValue = param.split("=");
                    String key = URLDecoder.decode(keyValue[0], "UTF-8");
                    String value = "";
                    if (keyValue.length > 1) {
                        value = URLDecoder.decode(keyValue[1], "UTF-8");
                    }
                    paramMap.put(key, value);
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return paramMap;
    }

    public String convertRussianMonthToNumeric(String russianMonth) {
        Locale russianLocale = new Locale("ru", "RU");
        DateFormatSymbols dateFormatSymbols = new DateFormatSymbols(russianLocale);
        String[] months = dateFormatSymbols.getMonths();

        for (int i = 0; i < months.length; i++) {
            if (russianMonth.contains(months[i])) {
                int monthNumber = i + 1;
                return String.format("%02d", monthNumber);
            }
        }

        return "00"; // 월이 유효하지 않은 경우
    }

    /**
     * jsoup을 사용하여 html 주석 제거 처리
     *
     * @param elements Jsoup Elements
     */
    public void removeComments(Elements elements) {
        for (Element element : elements) {
            removeComments(element);
        }
    }

    /**
     * jsoup을 사용하여 html 주석 제거 처리
     *
     * @param node Jsoup Node
     */
    public void removeComments(Node node) {
        for (int i = 0; i < node.childNodeSize(); ) {
            Node child = node.childNode(i);
            if (child instanceof TextNode) {
                i++;
            } else {
                if (child.nodeName().equals("#comment")) {
                    child.remove();
                } else {
                    removeComments(child);
                    i++;
                }
            }
        }
    }

    /**
     * pdf 를 제외한 href 속성 제거 (Elements 용)
     *
     * @param contentHtml Elements 변수
     */
    public void removeHrefExceptPDF(Elements contentHtml) {
        Elements links = contentHtml.select("a");
        for (Element link : links) {
            String hrefValue = link.attr("href");
            if (hrefValue != null && !hrefValue.contains(".pdf")) {
//				link.removeAttr("href");
                int pdfIndex = hrefValue.lastIndexOf(".pdf");
                if (pdfIndex != -1) {
                    // Extract only the part before ".pdf"
                    String newHrefValue = hrefValue.substring(0, pdfIndex + 4);
                    link.attr("href", newHrefValue);
                } else {
                    // No ".pdf" found, set href to "#"
                    link.unwrap();
                }
            }
        }
    }

    /**
     * pdf 를 제외한 href 속성 제거 (Element 용)
     *
     * @param contentHtml Element 변수
     */
    public void removeHrefExceptPDF(Element contentHtml) {
        Elements links = contentHtml.select("a");
        for (Element link : links) {
            String hrefValue = link.attr("href");
            if (hrefValue != null && !hrefValue.contains(".pdf")) {
//				link.removeAttr("href");
                int pdfIndex = hrefValue.lastIndexOf(".pdf");
                if (pdfIndex != -1) {
                    // Extract only the part before ".pdf"
                    String newHrefValue = hrefValue.substring(0, pdfIndex + 4);
                    link.attr("href", newHrefValue);
                } else {
                    // No ".pdf" found, set href to "#"
                    link.unwrap();
                }
            }
        }
    }

    // 중복되는 a태그 한개만 남기고 제거
    public void removeDuplicateHref(Elements contentHtml) {
        Elements links = contentHtml.select("a"); // "a" 태그 요소 선택
        Set<String> uniqueLinks = new HashSet<>();

        for (Element link : links) {
            String href = link.attr("href");
            if (!uniqueLinks.contains(href)) {
                uniqueLinks.add(href);
            } else {
                link.remove(); // 중복되지 않은 경우에만 요소 제거
            }
        }
    }

    /**
     * String 값을 CRC로 변환하여 리턴한다.
     *
     * @param text CRC 값으로 변경할 대상 String 변수 (sourceId + title을 넣을 것)
     * @return 변환된 CRC String 값
     */
    public String stringToCrcId(String text) {
        CRC32 crc32 = new CRC32();
        crc32.update(text.getBytes()); // 입력 문자열을 바이트 배열로 변환하여 CRC를 계산
        long crcValue = crc32.getValue();
        String id = Long.toString(crcValue);

        return id;
    }

    /**
     * request header에 필요한 User-Agent 값을 랜덤으로 생성한다.
     *
     * @return 랜덤 생성된 User-Agent 값
     */
    public String generateRandomUserAgent() {
        String os = getRandomWeightedValue(OS_WEIGHTS);
        String browser = getRandomWeightedValue(BROWSER_WEIGHTS);
        String version = getRandomWeightedValue(VERSION_WEIGHTS);
        return "Mozilla/5.0 (" + os + ") AppleWebKit/537.36 (KHTML, like Gecko) " + browser + "/" + version + " Safari/537.36";
    }

    /**
     * 가중치에 따라 리스트 내의 값 중 랜덤으로 하나를 리턴한다.
     *
     * @param weightLists 랜덤으로 출력할 값 및 랜덤 출현 가중치를 지정한 Map
     * @return 랜덤 생성된 User-Agent 값
     */
    private String getRandomWeightedValue(Map<String, Integer> weightLists) {
        Random random = new Random();
        Set<String> keySet = weightLists.keySet();
        int totalWeight = weightLists.values().stream().mapToInt(Integer::intValue).sum();
        int randomWeight = random.nextInt(totalWeight) + 1;
        int cumulativeWeight = 0;

        for (String key : keySet) {
            cumulativeWeight += weightLists.get(key);
            if (randomWeight <= cumulativeWeight) {
                return key;
            }
        }

        // Fall back to selecting a random value if weights are not properly configured
        String randomKey = "";
        int randomIdx = random.nextInt(keySet.size());
        int checkIdx = 0;
        Iterator<String> keyIterator = keySet.iterator();
        while (keyIterator.hasNext()) {
            randomKey = keyIterator.next();
            if (randomIdx == checkIdx) {
                break;
            }
            checkIdx++;
        }

        return randomKey;
    }

    /**
     * 시간 문자열이 올바른 형식(yyyy-MM-dd HH:mm:ss)인지 체크한다.
     *
     * @param input 시간 문자열
     * @return 올바른 형식 여부
     */
    public boolean isValidDateFormat(String input) {
        // 지정된 날짜 및 시간 형식
        String dateFormat = "yyyy-MM-dd HH:mm:ss";

        // SimpleDateFormat을 사용하여 주어진 형식으로 문자열을 날짜로 파싱
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        sdf.setLenient(false); // 엄격한 파싱 모드 설정

        try {
            Date date = sdf.parse(input);
            // 예외가 발생하지 않으면 형식이 올바르다고 판단
            return true;
        } catch (ParseException e) {
            // ParseException이 발생하면 형식이 잘못된 것으로 판단
            return false;
        }
    }

    /**
     * 문자열로 표기된 월 표기를 두자리 숫자 단위로 변환시킨다.
     *
     * @param month 월 문자열표기 입력값
     * @return String 월 두자리 숫자 표기값
     */
    public String convertEnglishMonthToNumber(String month) {
        String monthUpperCase = month.toUpperCase();
        Map<String, String> monthMap = new HashMap<>();
        monthMap.put("JANUARY", "01");
        monthMap.put("JAN", "01");
        monthMap.put("FEBRUARY", "02");
        monthMap.put("FEB", "02");
        monthMap.put("MARCH", "03");
        monthMap.put("MAR", "03");
        monthMap.put("APRIL", "04");
        monthMap.put("APR", "04");
        monthMap.put("MAY", "05");
        monthMap.put("JUNE", "06");
        monthMap.put("JUN", "06");
        monthMap.put("JULY", "07");
        monthMap.put("JUL", "07");
        monthMap.put("AUGUST", "08");
        monthMap.put("AUG", "08");
        monthMap.put("SEPTEMBER", "09");
        monthMap.put("SEP", "09");
        monthMap.put("OCTOBER", "10");
        monthMap.put("OCT", "10");
        monthMap.put("NOVEMBER", "11");
        monthMap.put("NOV", "11");
        monthMap.put("DECEMBER", "12");
        monthMap.put("DEC", "12");
        if (monthMap.containsKey(monthUpperCase)) {
            return monthMap.get(monthUpperCase);
        } else {
            return month;
        }
    }

    /**
     * 파라미터 url 값이 포함된 이미지 태그 삭제
     *
     * @param contentHtml Jsoup Elements
     * @return url 삭제 대상 이미지 태그 체크할 url 값
     */
    public void removeImgTag(Elements contentHtml, String url) {
        Elements images = contentHtml.select("img");
        for (Element image : images) {
            String srcValue = image.attr("src");
            if (srcValue != null && srcValue.contains(url)) {
                image.remove();
            }
        }
    }

    /**
     * 수집 시 Link 문맥 Pre에 넣을 스타트 태그 값을 가져온다.
     *
     * @return String 스타트 태그 값
     */
    public String getListStartTag() {
        return LIST_START_TAG;
    }

    /**
     * 수집 시 Link 문맥 Post에 넣을 엔드 태그 값을 가져온다.
     *
     * @return String 앤드 태그 값
     */
    public String getListEndTag() {
        return LIST_END_TAG;
    }

    /**
     * 수집 시 Link 문맥 Post에 넣을 엔드 태그 값을 가져온다. (개행 문자 없는 걸로)
     *
     * @return String 앤드 태그 값
     */
    public String getListEndTagNoLf() {
        return LIST_END_TAG.replace("\n", "");
    }

    /**
     * ISPIDER4 폴더의 list 폴더 내의 CSV 파일을 읽어 수집 목록을 생성한다. (옛날 데이터 재수집용)
     *
     * @param sourceId 수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
     * @param number 파일 뒤 파일 순서 번호
     * @param domainUrl 도메인 URL
     * @param dummyUrl Dummy URL
     * @return 수집 목록
     * */
    public String getUrlListFileContent(String sourceId, String number, String domainUrl, String dummyUrl) {
        String content = "";
        content += getListStartTag() + "\n";
        String urlListFilePath = System.getenv("ISPIDER4_HOME") + "/list/" + sourceId + ".tsv";
        if (StringUtils.isNotBlank(number)) {   /* 2024-05-17 jhjeon: 같은 출처 내에 다른 extension 로직을 구현한 파일이 다수 있을 경우 각 경우의 파일명을 구분하기 귀한 값 추가 */
            urlListFilePath = System.getenv("ISPIDER4_HOME") + "/list/" + sourceId + "_" + number + ".tsv";
        }
        // 파일 존재 여부 확인
        File urlListFile = new File(urlListFilePath);
        if (!urlListFile.exists()) {
            System.out.println(sourceId + " TSV 파일이 존재하지 않습니다.");
        } else {
            try (BufferedReader br = new BufferedReader(new FileReader(urlListFilePath))) {
                String line;
                // 한 줄씩 읽어오기
                while ((line = br.readLine()) != null) {
                    // 쉼표(,)를 기준으로 데이터 분리
                    String[] lineArr = line.split("\t");
                    String title = lineArr[0];
                    String url = lineArr[1];
                    if (!StringUtils.isBlank(domainUrl) && StringUtils.isBlank(dummyUrl)) {
                        url = url.replace(domainUrl, dummyUrl);
                    }
                    content += "<a href=\"" + url + "\">" + title + "</a>\n";
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        content += getListEndTagNoLf();

        return content;
    }

    /**
     * ISPIDER4 폴더의 list 폴더 내의 CSV 파일을 읽어 수집 목록을 생성한다. (옛날 데이터 재수집용)
     * 수집 출처 코드 sourceId만 보낼 경우
     *
     * @param sourceId 수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
     * @return 수집 목록
     * */
    public String getUrlListFileContent(String sourceId) {
        return getUrlListFileContent(sourceId, "", "", "");
    }

    /**
     * ISPIDER4 폴더의 list 폴더 내의 CSV 파일을 읽어 수집 목록을 생성한다. (옛날 데이터 재수집용)
     * 수집 출처 코드 sourceId 및 파일 순서 목록 number만 보낼 경우
     *
     * @param sourceId 수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
     * @param number 파일 뒤 파일 순서 번호
     * @return 수집 목록
     * */
    public String getUrlListFileContent(String sourceId, String number) {
        return getUrlListFileContent(sourceId, number, "", "");
    }

    /**
     * 스레드 슬립
     *
     * @param millis 몇초(1000 = 1초)
     */
    public void threadSleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {}
    }

    /**
     * 스레드 슬립 (랜덤 시간)
     *
     * @param maxMillis 몇초 최대치 (1000 = 1초)
     * @param minMillis 몇초 최소치 (1000 = 1초)
     */
    public void threaSleep(int maxMillis, int minMillis) {
        try {
            Random random = new Random();
            int delay = random.nextInt(maxMillis - minMillis + 1) + minMillis; /* 5초에서 7초까지의 랜덤한 시간 */
            Thread.sleep(delay); // 랜덤한 시간 동안 스레드 대기
        } catch (InterruptedException e) {
            System.out.println("스레드 슬립 실패");
        }
    }

    /**
     * 구글 트렌드 수집 csv 파일 저장
     *
     * @param fileNamePreText (geo 파라미터)_(category 파라미터)
     * @param content 수집 데이터
     */
    public void saveGoogleTrendsCsv(String fileNamePreText, String content) {
        String folderName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 저장 경로 (ispider4/attach_backup/googletrends/YYYYMMDD)
        String attachFolderPath = ispider4Home + "/attach_backup/googletrends" + File.separator + folderName;

        // 폴더 생성
        File attachFolder = new File(attachFolderPath);
        if (!attachFolder.exists()) {
            boolean created = attachFolder.mkdirs();
            if (created) {
                System.out.println("ISPIDER4 attach 폴더를 생성했습니다: " + attachFolder);
            } else {
                System.out.println("ISPIDER4 attach 폴더를 생성을 실패했습니다: " + attachFolder);
            }
        }

        // 파일명: (geo)_(category)_YYYYMMDDHHMMSS.csv
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileName = fileNamePreText + "_" + timestamp + ".csv";

        File file = new File(attachFolder, fileName);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write(content);
            System.out.println("CSV 저장 완료: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 지정된 WebDriver에서 주어진 셀렉터(By)가 존재하는지 검증
     * 요소가 존재하지 않으면 RuntimeException을 발생
     *
     * @param driver   WebDriver 인스턴스
     * @param selector 찾고자 하는 Selenium {@link By} 셀렉터
     * @param sourceId 해당 사이트 sourceId
     * @throws RuntimeException 요소를 찾을 수 없을 경우
     */
    public void assertExists(WebDriver driver, By selector, String sourceId) {
        try {
            driver.findElement(selector);
        } catch (NoSuchElementException e) {
            throw new RuntimeException(sourceId + " Can not find selector", e);
        }
    }

    /**
     * 지정된 Jsoup Document에서 CSS Query가 존재하는지 검증
     * 요소가 존재하지 않으면 RuntimeException을 발생
     *
     * @param document Jsoup {@link Document} 인스턴스
     * @param cssQuery 찾고자 하는 CSS 선택자
     * @param sourceId 해당 사이트 sourceId
     * @throws RuntimeException 요소를 찾을 수 없을 경우
     */
    public void assertExists(Document document, String cssQuery, String sourceId) {
        Elements elements = document.select(cssQuery);
        if (elements.isEmpty()) {
            throw new RuntimeException("sourceId : " + sourceId + " Can not find cssQuery");
        }
    }










}
