package extension;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
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
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import javax.net.ssl.HttpsURLConnection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.main.BbsMain;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.connect.ProxySetting;
import com.diquest.ispider.common.exception.ISpiderException;
import com.diquest.ispider.common.save.structure.RNode;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

public class CommerceExtension_attachTest implements Extension{
	private int p = 0;//페이징
	private ConnectionUtil connectionUtil = new ConnectionUtil();
	private AttachUtil attachUtil = new AttachUtil();
	private String cl_cd;
	private String origin_cd;
	private boolean error_exist = false;
	private int doc_id;
	private List<HashMap<String, String>> attaches_info;
	private String now_time;
	private String file_name;
	private String content_url;
	
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
			content_url = dqPageInfo.getUrl();
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
		//날짜 바꾸기
		// <time datetime="2022-10-31T16:12:27Z" class="datetime">
		// <time dateTime=\"(.*?)\"
		try {
			for (int i = 0; i < row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();

				//날짜 바꾸기
				if (nodeName.equals("created_date") && !nodeValue.equals("") && nodeValue!=null) {
					LocalDateTime localtime = LocalDateTime.from(Instant
							.from(DateTimeFormatter.ISO_DATE_TIME.parse(nodeValue)).atZone(ZoneId.of("Asia/Seoul")));

//					row.getNodeByIdx(i).setValue(localtime.toString().replace("T", " "));
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
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		
	}

	//8
	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		boolean isCheck = true;
		List<HashMap> attach_list = new ArrayList<>();
		List<HashMap> attach_temp_list = new ArrayList<>();
		String new_images="";

		/*
		 * TODO 첨부파일 url 정보를 가지고 첨부파일을 다운로드하고 원본 url 과 저장한 첨부파일명을 테이블에 쌓기
		 */
		try {
			String title="";
			String category_id = origin_cd;
			String collector_id = String.format("%02d", Integer.parseInt("1"));
			String bbs_id = String.format("%04d", Integer.parseInt(dqPageInfo.getBbsId()));
			String document_id = String.format("%06d", doc_id);
			
			String attach_name_prefix = "WEB" + collector_id + "_" + cl_cd + category_id + "_" + bbs_id + "_" + now_time;
			
			for (int i = 0; i < row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();

				if (nodeName.equals("title") ) {
					title = nodeValue;
				}
				
				//2023-01-05 
				//첨부파일 로직 변경
				if (nodeName.equals("image_path") && nodeValue != null && !nodeValue.equals("")) {
					String [] image_pathes = nodeValue.split("\n");
					
					//첨부파일 정보 
					for(int idx=0; idx<image_pathes.length; idx++) {
						HashMap<String, String> attach = new HashMap<>();
						attach.put("document_id", String.format("%06d", doc_id));
						attach.put("image_path", image_pathes[idx]);
						attach.put("index", String.format("%03d", idx+1));
						attach.put("image_prefix", attach_name_prefix);
						
						//테이블 적재용 정보
						attach.put("collect_date", now_time);
						attach.put("content_url", content_url);
						attach.put("origin_cd", origin_cd);
						attach.put("cl_cd", cl_cd);
						
						attach_list.add(attach);
					}
				}
				
				if(nodeName.equals("images") && nodeValue != null && !nodeValue.equals("")) {
					String [] images = nodeValue.split("\n");
					for(int idx=0; idx<images.length; idx++) {
						HashMap<String, String> attach = new HashMap<>();
						String index = String.format("%03d", idx+1);
						String origin_name = images[idx];
						String ext = origin_name.substring(origin_name.lastIndexOf("."));
						
						attach.put("index", index);
						attach.put("origin_name", origin_name);
						attach.put("ext", ext);
						
						attach_temp_list.add(attach);
						
						//바뀐 파일명으로 images 값 업데이트
						new_images += attach_name_prefix+"_"+document_id+index+ext+"\n";
					}
					row.getNodeByIdx(i).setValue(new_images);
				}
			}
			
			for(int i=0; i<attach_list.size(); i++) {
				if(attach_list.size() != attach_temp_list.size()) {
					System.out.println("첨부파일 url과 첨부파일 수가 다릅니다!");
				} else {
					attach_list.get(i).put("origin_name", attach_temp_list.get(i).get("origin_name"));
					attach_list.get(i).put("ext", attach_temp_list.get(i).get("ext"));
				}
			}
			
			if(title.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount();	//에러 파일수 판단용
			}
			
			if(isCheck) {
				for(int idx=0; idx<attach_list.size(); idx++) {
					//첨부파일 다운로드
					attachUtil.downloadAttachFile(attach_list.get(idx), null);
					//첨부파일 검증
					attachUtil.validAttachFile(attach_list.get(idx));
				}
				
			}
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		
		return isCheck;
	}
	
	//9
	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		BbsMain bbsMain = Configuration.getInstance().getBbsMain(dqPageInfo.getBbsId());
		
		try {

			String category_id = origin_cd;
			String collector_id = String.format("%02d", 1);
			String bbs_id = String.format("%04d", Integer.parseInt(dqPageInfo.getBbsId()));
			
			file_name = "WEB"+collector_id+"_"+cl_cd+category_id+"_"+bbs_id+"_"+now_time;
			String origin_file_name = dqPageInfo.getBbsId()+"_"+bbsMain.getBbsName()+"_0";
			
			//수집로그 저장
			connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);
			
			connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name+".dqdoc");

			/*System.out.println("첨부파일 목록 : "+attaches_info.toString());
			
			//첨부파일 저장
			connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);*/
			
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
}
