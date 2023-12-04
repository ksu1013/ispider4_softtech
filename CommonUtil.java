package extension;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class CommonUtil {

    private static final Map<String, Integer> OS_WEIGHTS = new HashMap<>();
    private static final Map<String, Integer> BROWSER_WEIGHTS = new HashMap<>();
    private static final Map<String, Integer> VERSION_WEIGHTS = new HashMap<>();

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

    private ConnectionUtil connectionUtil;

    public CommonUtil() {
        connectionUtil = new ConnectionUtil();
    }

    /* 숫자 여부 체크  */
    public boolean isNumeric(String s) {
        return s.replaceAll("[+-]?\\d+", "").equals("") ? true : false;
    }

    public String getNumberByText(String text) {
        if (text == null || text.equals("")) {
            return null;
        }

        return text.replaceAll("[^0-9]", "");
    }

    /**
     * String to json
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

    public String getSubStringResult(String startTag, String endTag, String text) {


        String result = "";

        if (!text.contains(startTag) || !text.contains(endTag)) {
            return result;
        }
        String subStringText = null;
        try {
            int start = text.indexOf(startTag) + startTag.length();
            subStringText = text.substring(start);
            int end = subStringText.indexOf(endTag);
            result = subStringText.substring(0, end);
        } catch (Exception e) {
            e.printStackTrace();
            result = "";
        }

        return result;
    }

    // www.naver.com 여기까지만 가져오는것
    public String getDomain(String url) {
        String preText = "http://";
        if (url.contains("https://")) preText = "https://";
        String newUrl = url.substring(url.indexOf(preText) + preText.length());
        String newUrl2 = newUrl.substring(0, newUrl.indexOf("/"));

        String domain = preText + newUrl2;

        return domain;
    }

    // ? 앞까지.
    public String getFullDomain(String url) {
        String preText = "http://";
        if (url.contains("https://")) preText = "https://";
        String newUrl = url.substring(url.indexOf(preText) + preText.length());
        String newUrl2 = newUrl.substring(0, newUrl.indexOf("?"));

        String domain = preText + newUrl2;

        return domain;
    }

    /*
     * 추가적인 HTML 정보를 받아 오기 위한 http Connection
     *
     * */
    public String getProductDetailPage(String url, String encoding) {
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

    public String pickUpDate(String InputValue) {
        String value = "";
        String ParsingData = "";
        if (!(InputValue.equals("") && !(InputValue.equals(null)))) {
            Pattern DatePattern = Pattern
                    .compile("[0-9]{4}[- /. /년 ]*(0[1-9]|1[0-2]|[1-9])[- /. /월]*(0[1-9]|[1-2][0-9]|3[0-1]|[1-9])");
            Matcher DateMatcher = DatePattern.matcher(InputValue.replace(" ", ""));
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

    // 공백, 탭 , 개행 등 제거
    public String removeWhiteSpace(String input) {

        if (input == null || input.equals("")) {
            return input;
        }

        return input.replaceAll("(\r\n|\r|\n|\n\r|\\p{Z}|\\t)", "");
    }

    /*
     * 추가적인 HTML 정보를 받아 오기 위한 http Connection
     * 1페이지 접속은 되나 페이지 변경시 안될때 사용
     * url , page파라미터(ex: pageNo=2), encoding Type
     *
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
            hurlc.setReadTimeout(2000000);
            hurlc.setConnectTimeout(2000000);
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

    // create date 날짜 포맷 변경
    public String changeDateFormat(String datetimeHtml) {
        String datetime = "";

        if (datetimeHtml != null && !datetimeHtml.equals("")) {
            LocalDateTime localtime = LocalDateTime.from(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(datetimeHtml)).atZone(ZoneId.of("Asia/Seoul")));
            datetime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(localtime);
        }

        return datetime;
    }

    // 0뎁스 LIST 만들기 (Jsoup Elements)
    public String getUrlList(Elements urlList, String domain) {
        StringBuffer tagList = new StringBuffer();
        urlList = urlList.select("a[href]");

        tagList.append("<!--List Start-->");
        for (Element link : urlList) {
            String currentUrl = link.attr("href");
            if (currentUrl.contains(domain)) {
                tagList.append("\n<a href =\"" + currentUrl + "\">link</a>");
            } else {
                tagList.append("\n<a href =\"" + domain + currentUrl + "\">link</a>");
            }
        }
        tagList.append("\n<!--List End-->");

        return tagList.toString();
    }

    // 0뎁스 LIST 만들기 (Set<String>)
    public String getUrlList(Set<String> urlList, String domain) {
        StringBuffer tagList = new StringBuffer();
        tagList.append("<!--List Start-->");
        for (String link : urlList) {
            if (link.contains(domain)) {
                tagList.append("\n<a href =\"" + link + "\">link</a>");
            } else {
                tagList.append("\n<a href =\"" + domain + link + "\">link</a>");
            }
        }
        tagList.append("\n<!--List End-->");

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
     * (당장 쓸일없어서 주석처리)
     * 1, 2depth Tag String 만드는 함수
     * 기존 makeCollectContext 함수를 참고해서 만듬
     * @param tagMap 각 태그를 저장한 hashmap 변수
     * */
//	public String makeCollectContext(Map<String, String> tagMap) {
//		String tagList = "";
//		String startTag = "";
//		String endTag = "";
//		if (tagMap.containsKey("STARTTAG") && tagMap.containsKey("ENDTAG")) {
//			startTag = tagMap.get("STARTTAG");
//			endTag = tagMap.get("ENDTAG");
//			tagList = startTag + "\n";
//		}
//		for (String key : tagMap.keySet()) {
//			String value = tagMap.get(key);
//			tagList = "<" + key + ">" + value + "</" + key + ">\n";
//		}
//		if (tagMap.containsKey("STARTTAG") && tagMap.containsKey("ENDTAG")) {
//			tagList += endTag;
//		}
//
//		return tagList.toString();
//	}

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
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=");
                String key = URLDecoder.decode(keyValue[0], "UTF-8");
                String value = URLDecoder.decode(keyValue[1], "UTF-8");
                paramMap.put(key, value);
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

    // jsoup 으로 html 주석 제거
    public void removeComments(Elements elements) {
        for (Element element : elements) {
            removeComments(element);
        }
    }

    private void removeComments(Node node) {
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

    // pdf 를 제외한 href 속성 제거
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
	 * @param text    CRC 값으로 변경할 대상 String 변수 (title + content를 넣을 것)
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
     * @param   weightLists 랜덤으로 출력할 값 및 랜덤 출현 가중치를 지정한 Map
     * @return  랜덤 생성된 User-Agent 값
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
}
