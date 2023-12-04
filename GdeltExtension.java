package extension;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.comparator.DirectoryFileComparator;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;


public class GdeltExtension implements Extension{
	//개발
//	static final String PROXY_IP = "10.10.10.213";
	static final String PROXY_IP = "16.162.120.143";
	static final int PROXY_PORT = 3128;
	
	private ConnectionUtil connectionUtil = new ConnectionUtil();
//	private String file_name;
	private String cl_cd;
	private String origin_cd;
	public static final String FILE_PATH = "/mnt/nfs/collect/";
	public static final String FILE_NAME = "gdelt.txt";
	private List<String> file_list = new ArrayList<>();
	private static String searchDate ;
	private String searchDate_before;
	private String pattern;
	private String today;
	private boolean doCollect;
	
	//1
	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		try {
			System.out.println("StartExtension!!!!!");
			searchDate = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1).format(DateTimeFormatter.ofPattern(("yyyyMMdd")));
			searchDate_before = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2).format(DateTimeFormatter.ofPattern(("yyyyMMdd")));
			today = LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern(("yyyyMMdd")));
			
			doCollect = false;
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
		try {
			//파일로 저장해서 역순으로 뒤집기
			BufferedWriter writer = new BufferedWriter(new FileWriter("tempFile.txt"));
			writer.write(htmlSrc);
			writer.close();
			
			File temp_file  = new File("tempFile.txt");
			System.out.println("temp_file : "+temp_file.length());
			ReversedLinesFileReader reader = new ReversedLinesFileReader(temp_file, Charset.forName("UTF-8"));
			
			String file_path = "/mnt/nfs/collect/gdelt";
			File gdelt_dir = new File(file_path);
			
			String zip_url = "";
			int i = 0;
			String lastCollectDate = fileReader();
			boolean download_suc = true;
			List<String> zip_infos = new ArrayList<>();
			
			if(lastCollectDate == null || Integer.parseInt(lastCollectDate) <= Integer.parseInt(searchDate)) {
				boolean end = false;
				//전전날이 나오기 전까지 가져오기
				while(!end) {
					String line = reader.readLine();
					if(line != null && !line.equals("")) {
						String[] file_infos = line.split(" ");
						if(file_infos.length == 3) {
							String file_url = file_infos[2];
							String origin = file_url.substring(file_url.lastIndexOf("/")+1);
							if(origin.startsWith(searchDate_before)) {
								end = true;
							} else {	//전날 데이터만 가져오기
								if(origin.startsWith(searchDate) && origin.indexOf("gkg.csv.zip")>0) {
									zip_infos.add(line);
								}
							}
						}
					}
				}
				System.out.println(zip_infos.size());
				for(String zip_info : zip_infos) {
					if(zip_info.split(" ").length == 3) {	//최근 3개 파일
						zip_url = zip_info.split(" ")[2];

						if(zip_url.indexOf("gkg.csv.zip")>0) {
							//zip파일 다운로드
							String[] file_name_arr = zip_url.split("/");
							String file_name = file_name_arr[file_name_arr.length-1];
							downloadFile(zip_url, file_path, file_name);
							
							file_list.add(zip_info.split(" ")[0]+"_"+file_name);
						}
					}
				}
				
				int idx = 0;
				for(String file_data:file_list) {
					String[] file_datas = file_data.split("_");
					int file_size = Integer.parseInt(file_datas[0]);
					Path path = Paths.get(file_path+"/"+file_datas[1]);
					long bytes = Files.size(path);
					
					if(bytes != file_size) {	//파일 검증
						download_suc = false;
						break;
					}
					if(idx == 0) {
						String data = file_datas[1];
						String[] pattern_arr = data.split("00.");
						pattern = "00."+pattern_arr[1];
						System.out.println("ChangeHtml pattern : "+pattern);
						idx++;
					}
				}
				if(download_suc) {
					fileWriter(today);
					doCollect = true;
				}
			
			} else {
				//최종 수집일이 오늘일 때 => 수집 다시 안해도 됨
				System.out.println("이미 수집주기에 수집이 완료되었습니다.");
			}
			htmlSrc = "<attachfile></attachfile>\n<title></title>\n<url>url</url>";
		}catch(Exception e) {
			e.printStackTrace();
		
		}
				
		
		return htmlSrc;

	}
	
	//5
	@Override
	public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
		System.out.println("makeNewUrls!!!!");
		List<String> urls = new ArrayList<String>();
		urls.add(naviUrl);
		return urls;
	}
	
	//6
	@Override
	public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
		System.out.println("splitRow!!!!");
		return null;
	}

	//7
	@Override
	public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
		System.out.println("changeRowValue!!!!");
		for (int i = 0; i < row.size(); i++) {
			String nodeId = row.getNodeByIdx(i).getId();
			String nodeName = row.getNodeByIdx(i).getName();
			String nodeValue = row.getNodeByIdx(i).getValue();
			
			if(nodeName.equals("cl_cd")) {
				cl_cd = nodeValue;
			}else if(nodeName.equals("origin_cd")) {
				origin_cd = nodeValue;
			}
			
		}
	}

	//8
	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		boolean isCheck = false;
		System.out.println("validData");
		return isCheck;
	}

	//9
	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		System.out.println("endExtension");
		try {
			if(doCollect == true) {	//수집이 일어났을 떄에만 모니터링 로그 적재
				//FIN파일 생성
				String dirPath = "/mnt/nfs/collect/gdelt";
				String lastCollectDate = fileReader();
				File file = new File(dirPath);
				int file_cnt = folderFileCount(file, pattern);
				System.out.println("file_cnt : "+file_cnt);
				List<String> file_names = new ArrayList<>();
				
				//FIN 파일은 언제 생성할지
				if(lastCollectDate.equals(today)){
					for(String file_data:file_list) {
						String file_name = file_data.split("_")[1];
						
						connectionUtil.makeFinFile(dirPath, file_name);
						file_names.add(file_name);
					}
				}
				// 수집로그 저장
				connectionUtil.makeGdeltCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, file_names, false);
			}
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * gdelt 디렉토리 내의 파일 갯수
	 * 수집된 파일 분석기에서 가져가면 디렉토리에 존재하지 않을 것
	 * 파일 갯수 == 현재 수집 주기에서 수집한 파일
	 * @param directory
	 * @return
	 */
	public static int folderFileCount(File directory, String pattern) {
	    int cnt = 0;
	    try {
	    	System.out.println("pattern : "+pattern);
	    	if(directory.listFiles().length > 0) {
	    		for (File file : directory.listFiles()) {
		            if (file.isFile() && file.getName().matches(searchDate+"(.*)00.gkg.csv.zip")) {
//		            if (file.isFile() && file.getName().contains(pattern)) {
		            	cnt++;  //폴더 내부 파일 갯수
		            }
		        }		
	    	}
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
	    return cnt;
	}
	
	/**
	 * URL 파일 다운로드
	 * @param fileUrl
	 * @param filePath
	 * @param fileName
	 */
	public void downloadFile(String fileUrl, String filePath, String fileName) {
		try {
			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_IP, PROXY_PORT));
			URL download_url = new URL(fileUrl);
			HttpURLConnection con = (HttpURLConnection) download_url.openConnection(proxy);
			ReadableByteChannel rbc = Channels.newChannel(con.getInputStream());
			
			File dir = new File(filePath);
			if(!dir.exists()) {
				System.out.println("Directory is not exist!!");
				dir.mkdir();
			}
			
			fileName = filePath+"/"+fileName;
			FileOutputStream fos = new FileOutputStream(fileName);
			System.out.println("downloadFile > fileName : "+fileName);
			
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();
			Thread.sleep(3000);
		} catch (IOException e) {
			e.printStackTrace();
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * 날짜 정보 파일 읽어오기
	 * @return
	 */
	public static String fileReader() {

		File file = new File(FILE_PATH + File.separator + FILE_NAME);
		String lastColctDate = "";

		try {
			Scanner scan = new Scanner(file);
			while (scan.hasNextLine()) {
				lastColctDate += scan.nextLine();
			}
		} catch (FileNotFoundException e) {
			lastColctDate = "0";
		}
		return lastColctDate;
	}
	
	
	/**
	 * 날짜 정보 파일에 쓰기
	 * @param targetDate
	 */
	public static void fileWriter(String targetDate) {
		File file = new File(FILE_PATH);

		if (!file.exists()) {
			// 경로가 없다면 생성합니다. (디렉토리)
			try {
				file.mkdirs();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		FileWriter writer = null;
		try {
			writer = new FileWriter(file + "/" + FILE_NAME, false);
			writer.write(targetDate);

			writer.flush();

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (writer != null) {
					writer.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
