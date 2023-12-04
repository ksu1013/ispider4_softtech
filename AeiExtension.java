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
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;

/**
 * JS1_PXAEI
 * @author 이가원
 *
 */
public class AeiExtension implements Extension, AddonExtension{
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
		map.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
		map.put("Cookie", "_uid=144362359.1680756564071; _gid=GA1.2.1749624538.1680756566; _mkto_trk=id:475-PBQ-971&token:_mch-aei.org-1680756566342-60677; ln_or=eyI5ODEyIjoiZCJ9; _fbp=fb.1.1680756566599.939388290; __pdst=dd92ecde8a0d4719be0a41c3967c984a; __qca=P0-1722548768-1680756566166; _hjSessionUser_676308=eyJpZCI6IjJhMTU0MWM5LTczZGEtNTUyZS04OTM2LTJiMWJjOWMxYmJhMiIsImNyZWF0ZWQiOjE2ODA3NTY1Njk2MTEsImV4aXN0aW5nIjp0cnVlfQ==; cf_chl_2=c638e2893d6d954; cf_clearance=t9no21iHxvQ79nQCTz_dSHTwML6HUy4nzkDLz4LvfSE-1680830166-0-160; __cf_bm=hxh1RkN.ohPA9vqOy52Guyb0HygPjJZoxV6r3_jPWZY-1680830177-0-AWWIF4MsfW47bL/Obo4w7KztdIylqceMc2aQauViKgmJL2eCakE3NW82PIwkHnb4h9HJtOJ8Or+/5JkuHahs308XydhniY0LJh/aHdIo7DCnNKJMQSMDjQ9sjdz7ey4Z9g==; _hjIncludedInSessionSample_676308=0; _hjSession_676308=eyJpZCI6IjRkZjc2N2QxLThlMjEtNDA5Ni04NTJlLWM3YmZiZTAwYjIzNCIsImNyZWF0ZWQiOjE2ODA4MzAxOTAwODksImluU2FtcGxlIjpmYWxzZX0=; _hjIncludedInPageviewSample=1; _hjAbsoluteSessionInProgress=0; _ga_6ETZZCENM7=GS1.1.1680830178.2.1.1680830215.0.0.0; _ga=GA1.2.144362359.1680756564071; trwv.uid=americanenterpriseinstitute-1680756571567-d7baabe7%3A2; trwsa.sid=americanenterpriseinstitute-1680830189640-d5705bbe%3A2");
		map.put("Host", "www.aei.org");
		map.put("Cache-Control", "max-age=0");
		map.put("Accept-Language", "ko-KR,ko;q=0.9");
		map.put("Accept", "*/*");
		map.put("upgrade-insecure-requests", "1");
		map.put("Referer", "https://www.aei.org");
		return map;
	}
	
	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		System.out.println("changeHtml!!");
		Document document = Jsoup.parse(htmlSrc);
		document.getElementsByTag("iframe").remove();
		
		if(dqPageInfo.getParentUrl() != null && !dqPageInfo.getParentUrl().equals("")) {
			//제목
			String title = "";
			if(document.getElementsByClass("entry-title") != null && document.getElementsByClass("entry-title").size() > 0 && title.equals("")){
				title = document.getElementsByClass("entry-title").text();
			}else if(document.getElementsByClass("podcast-header") != null && document.getElementsByClass("podcast-header").size() > 0 && title.equals("")){
				title = document.getElementsByClass("podcast-header").get(0).getElementsByTag("h1").text();
			}
			
			//내용 
			String content = "";
			if(document.getElementsByClass("entry-content") != null && document.getElementsByClass("entry-content").size() > 0 && content.equals("")){
				content = document.getElementsByClass("entry-content").html();
			}else if(document.getElementsByClass("accordion-item") != null && document.getElementsByClass("accordion-item").size() > 0 && content.equals("")){
				content = document.getElementsByClass("accordion-item").html();
			}
			
			//작성일
			String datetime = "";
			if(document.getElementsByAttributeValue("property", "article:modified_time") != null && !document.getElementsByAttributeValue("property", "article:modified_time").equals("")) {
				datetime = document.getElementsByAttributeValue("property", "article:modified_time").attr("content");
			} else {
				datetime = document.getElementsByAttributeValue("property", "article:published_time").attr("content");
			}
			
			LocalDateTime localtime = LocalDateTime.from(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(datetime)).atZone(ZoneId.of("Asia/Seoul")));
			datetime = localtime.toString().replace("T", " ");
			
			htmlSrc = "<title>"+title+"</title><content>"+content+"</content><datetime>"+datetime+"</datetime>";
		
		} else {
			Elements list = document.getElementsByTag("h4");

			htmlSrc = "<list>\n";
			for(Element cont : list) {
				htmlSrc += cont.getElementsByTag("a")+"\n";
			}
			htmlSrc += "</list>";
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
					
					content_images = Arrays.asList(nodeValue.split("\n"));
					int image_idx=0;
					for(int idx = 0; idx<content_images.size(); idx++) {
						String image = content_images.get(idx);
						System.out.println("image : "+image);
						if(!image.substring(image.indexOf("_")+1).startsWith(".")) {
							content_images.set(image_idx, image.substring(image.indexOf("_")+1));
							image_idx ++;
						}
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
	
	@Override
	public Map<String, String> addAttachFileRequestHeader(String url) {
		if(url.contains("aei.org"))
			return getHeader();
		return null;
	}
	
	public HashMap<String, String> getHeader() {
		HashMap<String, String> headers = new HashMap<>();
		headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
		headers.put("Accept-Encoding", "gzip, deflate, br");
		headers.put("Accept-Language", "ko-KR,ko;q=0.9");
		headers.put("Connection", "keep-alive");
		headers.put("Cookie", "_uid=193792641.1681198051538; _gid=GA1.2.1632310195.1681198055; _fbp=fb.1.1681198055918.2128607307; __pdst=5d2967b7846a40e9aef1488a185f4cff; _mkto_trk=id:475-PBQ-971&token:_mch-aei.org-1681198056903-55107; ln_or=eyI5ODEyIjoiZCJ9; __qca=P0-1057787289-1681198056056; _hjSessionUser_676308=eyJpZCI6IjEzNWRiYjdmLTE1MmEtNWUyMS04YmMwLWY5MjU0NTczZGE5MCIsImNyZWF0ZWQiOjE2ODExOTgwNTgyMjksImV4aXN0aW5nIjp0cnVlfQ==; _ga_6ETZZCENM7=GS1.1.1681198053.1.1.1681198137.0.0.0; _ga=GA1.2.193792641.1681198051538; trwv.uid=americanenterpriseinstitute-1681198062500-bbbd2f49%3A1");
		headers.put("Host", "www.aei.org");
		headers.put("Upgrade-Insecure-Requests", "1");
		headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
		
		return headers;
	}
	
}
