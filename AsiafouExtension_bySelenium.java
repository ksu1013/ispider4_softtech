package extension;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

/**
 * JS1_PXASIAFOU
 * @author 이가원
 *
 */
public class AsiafouExtension_bySelenium implements Extension{
	 private WebDriver driver = null;
	// 크롤링 할 URL
	private String base_url;
	private String cl_cd;
	private String origin_cd;
	private ConnectionUtil connectionUtil = new ConnectionUtil();
	private boolean error_exist = false;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;
	private int doc_id;
	    
	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		try {
			System.out.println("StartExtension!!!!!");
		    String WEB_DRIVER_ID = "webdriver.chrome.driver";
		    String WEB_DRIVER_PATH = "/home/diquest/ispider4/conf/selenium/chromedriver";
		    
			System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH);
			ChromeOptions options = new ChromeOptions();
			options.addArguments("start-maximized"); // open Browser in maximized mode
			options.addArguments("disable-infobars"); // disabling infobars
			options.addArguments("headless");
			options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.67 Safari/537.36");
			options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
	        driver = new ChromeDriver(options);
	        
			Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
			attaches_info = new ArrayList<>();
			doc_id = 0;
			
			Date now = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
			now_time = sdf.format(now);
	        
		}catch (Exception e) {
			System.out.println("chrome driver error!!!!");
			e.printStackTrace();
			if(driver != null) {
				driver.quit();
			}
		}
	}

	@Override
	public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
		
		return url;
	}
	
	@Override
	public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
		Map<String, String> map = new HashMap<String, String>();
		
		map.put("cookie", "_ga_5V7G8SXH1W=GS1.1.1678251536.1.0.1678251536.0.0.0; _ga=GA1.2.434750613.1678251537; _gid=GA1.2.2101721900.1678251537; _gcl_au=1.1.1476294398.1678251537; _gat_gtag_UA_3801138_1=1");
		map.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36");
		map.put("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
		return map;
	}
	
	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		try {
			base_url = dqPageInfo.getUrl();
			driver.get(base_url);
			driver.manage().timeouts().implicitlyWait(15, TimeUnit.SECONDS);
			
			htmlSrc = driver.getPageSource();
			
			if(dqPageInfo.getParentUrl() != null && !dqPageInfo.getParentUrl().equals("")) {
				doc_id ++;
				Document document = Jsoup.parse(htmlSrc);
				document.getElementsByTag("noscript").remove();
				String title = "";
				String content = "";
				String datetime = "";
				
				if(document.getElementsByClass("et_pb_text_inner").size() > 0) {
					Elements ele = document.getElementsByClass("et_pb_text_inner");
					Element cont_ele = null;
					//본문 영역 찾기
					for(int i=0; i<ele.size(); i++) {
						if(ele.get(i).getElementsByTag("h1").size() > 0) {	//제목이 있을 떄
							cont_ele = ele.get(i);
						}
					}
					title = cont_ele.getElementsByTag("h1").text();
					cont_ele.getElementsByTag("h1").remove();	//본문에서 제목 제거
					cont_ele.getElementsByClass("dateMeta").remove(); 	//본문에서 날짜 제거
					cont_ele.getElementsByClass("singleSocial").remove(); 	//본문에서 sns 공유 제거
					cont_ele.getElementsByTag("iframe").remove(); 	//본문에서 iframe 제거
					content = cont_ele.outerHtml();
				
				} else {
					title = document.getElementsByClass("entry-title").text();
					content = document.getElementsByClass("entry-content").outerHtml();
				
				}
				
				//2023-04-19T20:15:10+00:00
				if(document.getElementsByAttributeValue("property", "article:published_time").size() > 0 && datetime.equals("")) {
					datetime = document.getElementsByAttributeValue("property", "article:published_time").attr("content");
				} else {
					datetime = document.getElementsByAttributeValue("property", "article:modified_time").attr("content");
				}
				
				SimpleDateFormat dtFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+00:00");
				SimpleDateFormat newDtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				
				Date formatDate = dtFormat.parse(datetime);
				datetime = newDtFormat.format(formatDate);
				
				htmlSrc = "<cont_title>"+title+"</cont_title>"+content+"<cont_datetime>"+datetime+"</cont_datetime>";
			} else {
				
				/** 추가로 목록이 필요한 경우
				 * https://asiafoundation.org/in-asia/more-posts?wpv_aux_current_post_id=42126&wpv_aux_parent_post_id=42126&wpv_view_count=42128-CATTRae7e11626b26ffa8d57c5fdc8a366a8a&wpv_paged=1 
				 * */
				
			}
			
		}catch(Exception e) {
			e.printStackTrace();
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
		try {
			for (int r = 0; r < row.size(); r++) {
				String nodeId = row.getNodeByIdx(r).getId();
				String nodeName = row.getNodeByIdx(r).getName();
				String nodeValue = row.getNodeByIdx(r).getValue();
				
				
				if (row.getNodeByIdx(r).getName().equals("source_class")) {
					cl_cd = nodeValue;
				}
				if (row.getNodeByIdx(r).getName().equals("source_ID")) {
					origin_cd = nodeValue;
				}
				if (nodeName.equals("document_id")) {
					row.getNodeByIdx(r).setValue(String.format("%06d", doc_id));
				}
				
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		boolean isCheck = true;
		//현재 문서 첨부파일
		List<String> content_images = new ArrayList<>();
		
		try {
			String title="";
			for (int i = 0; i < row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();

				if (nodeName.equals("title") ) {
					title = nodeValue;
				}
				
				if(nodeName.equals("images") && nodeValue != null && !nodeValue.equals("")) {
					HashMap<String, String> attach = new HashMap<>();
					
					content_images = connectionUtil.setAttachImageValue(attaches_info, attach, nodeValue, doc_id);
					
					file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
					
					//첨부파일명 저장
					String attachFiles = connectionUtil.setAttachNode(file_name, attach);
					
					System.out.println(attachFiles);
					row.getNodeByIdx(i).clearValue();
					row.getNodeByIdx(i).setValue(attachFiles);
				}
				
			}
		
			//=====================================================
			//현재 문서 첨부파일 매핑 위치 설정
			for(int i=0; i<row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();
				List<String> image_list = new ArrayList<>();		//본문에서 추출한 이미지 태그 목록
				
				if(nodeName.equals("content") && nodeValue != null && !nodeValue.equals("")) {
					String content = connectionUtil.getContainImageContent(nodeValue, content_images);
					
					row.getNodeByIdx(i).clearValue();
					row.getNodeByIdx(i).setValue(content);
				}
			}
			//=====================================================
			
			if(title.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount();	//에러 파일수 판단용
			}
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		return isCheck;
	}

	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		if(driver != null) {
			driver.quit();
		}
		
		file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
		String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);

		// 수집로그 저장
		connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);

		connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);

		System.out.println("첨부파일 목록 : " + attaches_info.toString());

		// 첨부파일 저장
		connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
	}
	
}
