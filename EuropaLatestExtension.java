package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @title Europa (유로파) 수집 그룹 extension (PXEUL)
 * @version 1.0 (2023-07-13)
 * @since 2023-07-13
 * @author 전제현
 */
public class EuropaLatestExtension implements Extension {
    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private String cl_cd;
    private String origin_cd;
    private boolean error_exist;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private WebDriver driver;
    private Random random;
	private static int MIN_DELAY = 4000;
	private static int MAX_DELAY = 5000;
	private static String DUMMY_HOST = "10.10.10.214";
	public static final String MAIN_URL = "https://ec.europa.eu/commission/presscorner/home/en?pagenumber=1";
	public static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
	public static final String WEB_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";
    private boolean isTest;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== EuropaLatestExtension Start ===");
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        if (connectionUtil.isLocal()) {
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH + ".exe");
        } else {
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH);
        }
        error_exist = false;
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);
        random = new Random();
        isTest = connectionUtil.isTest(reposit);
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

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-default-apps");
        String userAgent = commonUtil.generateRandomUserAgent();
        options.addArguments("--user-agent=" + userAgent); /* User-Agent 설정 랜덤생성 후 추가 */
        if (dqPageInfo.getParentUrl() == null) {    /* PAGE-LIST */
   			WebDriver driver = null;
   			try {
   				driver = new ChromeDriver(options);
   				driver.get(dqPageInfo.getUrl());
   				WebDriverWait wait = new WebDriverWait(driver, 60);
   				wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("news-block")));
   				Document document = Jsoup.parse(driver.getPageSource());
   				Elements urlList = document.getElementsByClass("ecl-list-item__link");
   				String domain = commonUtil.getDomain(dqPageInfo.getUrl());
   				domain += "/commission/presscorner/";
   				htmlSrc = commonUtil.getUrlList(urlList, domain);
   			} catch (Exception e) {
   				e.printStackTrace();
   			} finally {
   				if (driver != null) {
   					driver.quit();
   				}
   			}
   		} else {    /* PAGE-CONTENT */
   			String reformCreatedDateStr = "";
   			WebDriver driver = null;
   			try {
   				driver = new ChromeDriver(options);
   				driver.get(dqPageInfo.getUrl());
   				WebDriverWait wait = new WebDriverWait(driver, 60);
   				wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("skip-link")));
   				Document doc = Jsoup.parse(driver.getPageSource());
   				String newHtmlSrc = "<CONTENT-PAGE>\n";
   				/* 제목(title) 수집 */
   				String titleArea = doc.select("title").first().ownText();
   				String title = titleArea;
   				newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
   				/* 내용(content) 수집 */
   				Elements contentElement = doc.getElementsByClass("ecl-paragraph");
   				String content = contentElement.html();
//   				Elements contentImgElement = doc.getElementsByClass("ep_image");
//   				String content_img = contentImgElement.html();
   				newHtmlSrc += "<CONTENT>" +content + "</CONTENT>\n";
   				/* 생성일(created_date) 수집 */
   				String dateTime = "";
   				Elements dateElement = doc.select("meta[name=Date]");
                String dateContent="";
   				if (!dateElement.isEmpty()) {
   			        Element metaElement = dateElement.first();
   			        dateContent = metaElement.attr("content");
   			    }
   				reformCreatedDateStr=dateContent+ " 00:00:00";
   				newHtmlSrc += "<CREATED_DATE>" + reformCreatedDateStr + "</CREATED_DATE>\n";
   				newHtmlSrc += "</CONTENT-PAGE>";
   				htmlSrc += newHtmlSrc;
   			} catch (Exception e) {
   				e.printStackTrace();
   			} finally {
   				if (driver != null) {
   					driver.quit();
   				}
   			}
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
            HashMap<String, String> header = new HashMap<>();
            header.put("User-Agent", commonUtil.generateRandomUserAgent());
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
            System.out.println("=== EuropaLatestExtension End ===");
        }
    }
}
