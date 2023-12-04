package extension;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.main.BbsMain;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.RNode;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

public class CommerceNewsExtension implements Extension{
	private int p = 0;//페이징
	private ConnectionUtil connectionUtil = new ConnectionUtil();
	private String cl_cd;
	private String origin_cd;
	private boolean error_exist = false;
	private int doc_id;
	private List<HashMap<String, String>> attaches_info;
	private String now_time;
	private String file_name;
	
	
	//1
	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		
		try {
			System.out.println("StartExtension!!!!!");
			Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
			doc_id = 0;
			attaches_info = new ArrayList<>();
			
			Date now = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			now_time = sdf.format(now);
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	
	
	//2
	@Override
	public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
		
		return url;
	}
	
	//3
	@Override
	public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
		Map<String, String> map = new HashMap<String, String>();
		
		System.out.println("addRequestHeader!!!!");
		return map;
	}
	
	//4
	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		System.out.println("changeHtml!!!!");
		
		if(dqPageInfo.getParentUrl()!=null) {
//			Document document = Jsoup.parse(htmlSrc);
//			htmlSrc = document.getElementsByClass("region-content").get(0).html();
//			htmlSrc = htmlSrc.replaceAll("data-history-node-id=.*?>", "");
			
			//region-content
			Document document = Jsoup.parse(htmlSrc);
			String title = document.getElementsByClass("page-title").get(0).text();
			
			Element content = document.getElementsByTag("article").get(0);
			Elements removes = content.getElementsByClass("field--name-field-issues");
			if(removes.size()>0) {
				content.getElementsByClass("field--name-field-issues").get(0).remove();
			}
			
			String time = document.getElementsByTag("time").attr("datetime");
			
			//release-infobox
			Elements infobox= content.getElementsByTag("release-infobox");
			if(infobox.size()>0) {
				content.getElementsByTag("release-infobox").remove();
			}
			
			htmlSrc = "<title>"+title+"</title>\n<content>"+content+"</content>\n<date>"+time+"</date>";
			
			doc_id ++;
		}
		
		return htmlSrc;

	}
	
	//5
	@Override
	public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
		List<String> urls = new ArrayList<String>();
		urls.add(naviUrl);
		return urls;
	}
	
	//6
	@Override
	public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
		return null;
	}

	//7
	@Override
	public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
		for (int i = 0; i < row.size(); i++) {
			String nodeId = row.getNodeByIdx(i).getId();
			String nodeName = row.getNodeByIdx(i).getName();
			String nodeValue = row.getNodeByIdx(i).getValue();
			
			if (nodeName.equals("content")) {
				String content = nodeValue.replaceAll("class=\".*?\">", "");
				
				row.getNodeByIdx(i).setValue(content);
			}
			
			//날짜 바꾸기 2020-06-22T08:00:00Z
			if (nodeName.equals("created_date") && !nodeValue.equals("") && nodeValue != null) {
				LocalDateTime localtime = LocalDateTime.from(Instant
						.from(DateTimeFormatter.ISO_DATE_TIME.parse(nodeValue)).atZone(ZoneId.of("Asia/Seoul")));

				nodeValue = localtime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
				row.getNodeByIdx(i).setValue(nodeValue.replace("T", " "));
			}
			
			
			if(nodeName.equals("cl_cd")) {
				cl_cd = nodeValue;
			}else if(nodeName.equals("origin_cd")) {
				origin_cd = nodeValue;
			}
			
			if(nodeName.equals("document_id")) {
				row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
			}
		}
	}

	//8
	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		boolean isCheck = true;
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
				
				String category_id = origin_cd;
				String collector_id = String.format("%02d", Integer.parseInt("1"));
				String bbs_id = String.format("%04d", Integer.parseInt(dqPageInfo.getBbsId()));
				
				file_name = "WEB"+collector_id+"_"+cl_cd+category_id+"_"+bbs_id+"_"+now_time+".dqdoc";
				
				//첨부파일명 저장
				String attachFiles = connectionUtil.setAttachNode(file_name, attach);
				System.out.println(bbs_id+"_"+doc_id+"_attachFiles : "+attachFiles);
				row.getNodeByIdx(i).clearValue();
				row.getNodeByIdx(i).setValue(attachFiles);
			}
		}
		
		if(title.equals("")) {
			isCheck = false;
			connectionUtil.upFailDocFileDownloadCount();	//에러 파일수 판단용
		}
		
		return isCheck;
	}
	
	//9
	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		HashMap<String, Object> log_info = new HashMap<>();
		BbsMain bbsMain = Configuration.getInstance().getBbsMain(dqPageInfo.getBbsId());
		
		try {
			Date now = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			String now_time = sdf.format(now);

			String category_id = origin_cd;
			String collector_id = String.format("%02d", Integer.parseInt("1"));
			String bbs_id = String.format("%04d", Integer.parseInt(dqPageInfo.getBbsId()));
			
			file_name = "WEB"+collector_id+"_"+cl_cd+category_id+"_"+bbs_id+"_"+now_time+".dqdoc";
			String origin_file_name = dqPageInfo.getBbsId()+"_"+bbsMain.getBbsName()+"_0";
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
