package extension;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.main.BbsMain;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

/**
 * PXAMEPROGRESS
 * JS1_PXREPORT
 * @author 이가원
 *
 */
public class AmeProgressExtension implements Extension{
	private String cl_cd;
	private String origin_cd;
	private ConnectionUtil connectionUtil = new ConnectionUtil();
	private boolean error_exist = false;
	private int doc_id;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;
	
	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("StartExtension!!!!!");
		Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		doc_id = 0;
		attaches_info = new ArrayList<>();
		
		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
		now_time = sdf.format(now);
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
		System.out.println("changeHtml!!");
		Document document = Jsoup.parse(htmlSrc);
		
		if(dqPageInfo.getParentUrl() != null && !dqPageInfo.getParentUrl().equals("")) {
			Element download_ele = null;
			
			for(int i=0; i<document.getElementsByClass("accordion2-entry").size(); i++) {
				Element element = document.getElementsByClass("accordion2-entry").get(i);
				if(element.getElementsByClass("accordion2-title").text().contains("Download")) {
					download_ele = element;
				}
			}
			
			document.getElementsByClass("accordion2-entry").remove();
			document.getElementsByTag("noscript").remove();
			document.getElementsByClass("footnotes1").remove();
			
			//제목
			String title = document.getElementsByClass("header2-title").get(0).text();
			
			//내용 
			String content = document.getElementsByClass("article1").html();
			
			Document document2 = Jsoup.parse(content);
			//a태그 텍스트 안의 '.'을 없애줘야함 (파일명 생성 이슈)
			Elements a_list = document2.getElementsByTag("a");
			for(int i=0; i<a_list.size(); i++) {
				if(a_list.get(i).text() != null && !a_list.get(i).text().equals("")) {
					a_list.get(i).text(a_list.get(i).text().replace(".", ""));
				}
			}
			
			content = document2.html();
			if(download_ele != null) {
				content += download_ele.outerHtml();
			}
			
			//작성일
			String datetime = "";
			if(document.getElementsByAttributeValue("property", "article:modified_time") != null && document.getElementsByAttributeValue("property", "article:modified_time").size() > 0 && !document.getElementsByAttributeValue("property", "article:modified_time").equals("")) {
				datetime = document.getElementsByAttributeValue("property", "article:modified_time").attr("content");
			}else if(document.getElementsByAttributeValue("property", "article:published_time") != null && document.getElementsByAttributeValue("property", "article:published_time").size() > 0 && !document.getElementsByAttributeValue("property", "article:published_time").equals("") && datetime.equals("")){
				datetime = document.getElementsByAttributeValue("property", "article:published_time").attr("content");
			} else {
				datetime = document.getElementsByClass("header2-brow").get(0).getElementsByTag("time").attr("datetime");
			}
		
			datetime = datetime.toString().replace("T", " ").replace("+00:00", "");
			
			if(!datetime.contains(":")) {
				datetime += " 00:00:00";
			}
			htmlSrc = "<title>"+title+"</title><content>"+content+"</content><datetime>"+datetime+"</datetime>";
		
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
		doc_id ++;
		for (int i = 0; i < row.size(); i++) {
			String nodeId = row.getNodeByIdx(i).getId();
			String nodeName = row.getNodeByIdx(i).getName();
			String nodeValue = row.getNodeByIdx(i).getValue();

			if(nodeName.equals("source_class")) {
				cl_cd = nodeValue;
			}else if(nodeName.equals("source_ID")) {
				origin_cd = nodeValue;
			}
			
			if(nodeName.equals("document_id")) {
				row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
			}
			
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
					attach.put("document_id", String.format("%06d", doc_id));
					attach.put("images", nodeValue);
					
					attaches_info.add(attach);
					
					//현재 문서 첨부파일
					content_images = Arrays.asList(nodeValue.split("\n"));
					for(int idx = 0; idx<content_images.size(); idx++) {
						String image = content_images.get(idx);
						content_images.set(idx, image.substring(image.indexOf("_")+1));
					}
					
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
		BbsMain bbsMain = Configuration.getInstance().getBbsMain(dqPageInfo.getBbsId());
		
		try {
			file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
			String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
			
			//수집로그 저장
			connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);
			
			connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);

			System.out.println("첨부파일 목록 : "+attaches_info.toString());
			
			//첨부파일 저장
			connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
}
