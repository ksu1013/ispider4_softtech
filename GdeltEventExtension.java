package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.apache.commons.io.input.ReversedLinesFileReader;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * @author 전제현
 * @version 1.1 (2023-11-28)
 * @title Gdelt 이벤트 파일 다운로드 Extension (PXGDE)
 * @since 2023-10-31
 */
public class GdeltEventExtension implements Extension {

    public static final String TEMP_FILE_NAME = "gdeltCopyForEvent.txt";
    public static final String EVENT_FILE_NAME = "gdeltEventCollectedTime.txt";
    public static final String TEMP_TRANS_FILE_NAME = "gdeltCopyForEventTrans.txt";
    public static final String EVENT_TRANS_FILE_NAME = "gdeltEventTransCollectedTime.txt";
    private ConnectionUtil connectionUtil;
    private List<String> exportFileList;
    private String cl_cd;
    private String origin_cd;
    private String extensionName;
    private String ispider4Home;
    private String tempFilePath;
    private String eventFileFolderPath;
    private String exportFileFolderPath;
    private String exportFileDqFolderPath;
    private String proxyIp;
    private String eventFileName;
    private String chkDownloadFilePattern;
    private DateTimeFormatter formatter;
    private LocalDateTime yesterdayDateTime;            /* 어제 시간 (세계표준시) */
    private LocalDateTime checkCollectDatetimeLimit;    /* 수집여부 체크 시간 (세계표준시, 현재 시간 15분전), 해당 시간이 이전 수집 시간 이후여야 수집이 작동한다. */
    private LocalDateTime checkCollectStartDateLimit;        /* 수집여부 체크 날짜 (시작), 수집여부 체크 날짜가 존재하는 경우 수집여부 체크 시간을 무시하고 수집여부 체크 날짜 기준으로 수집한다. */
    private LocalDateTime checkCollectEndDateLimit;        /* 수집여부 체크 날짜 (끝), 수집여부 체크 날짜가 존재하는 경우 수집여부 체크 시간을 무시하고 수집여부 체크 날짜 기준으로 수집한다. */
    private int proxyPort;
    private int scsCnt;
    private int failrCnt;
    private boolean doCollect;
    private boolean isTest;
    private boolean isFakeDownload; /* 로컬에서만 가능, true로 적용할 경우 export 파일을 실제로 다운로드하지는 않고 로그에서 다운로드했다는 메세지만 띄운다. */

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        String bbsId = dqPageInfo.getBbsId();
        Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
        BbsSetting setting = Configuration.getInstance().getBbsSetting(bbsId);
        extensionName = setting.getExtensionName().replace("extension.", "");
        System.out.println("=== " + extensionName + " Start ===");
        ispider4Home = homePath;
        connectionUtil = new ConnectionUtil();
        proxyIp = connectionUtil.getProxyIp();
        proxyPort = connectionUtil.getProxyPortNumber();
        isTest = connectionUtil.isTest(reposit);
        scsCnt = 0;
        failrCnt = 0;
        tempFilePath = ispider4Home + File.separator + TEMP_FILE_NAME;
        eventFileName = EVENT_FILE_NAME;
        chkDownloadFilePattern = "00.export.CSV.zip";
        if (connectionUtil.isLocal() || isTest) {   // 테스트용 경로
            eventFileFolderPath = ispider4Home + File.separator + "transfer" + File.separator + "gdelt";
            exportFileFolderPath = eventFileFolderPath + File.separator + "event";
            exportFileDqFolderPath = eventFileFolderPath + "_dq" + File.separator + "event";
        } else { // 실제 운영 경로
            eventFileFolderPath = "/mnt/nfs/transfer";
            exportFileFolderPath = eventFileFolderPath + File.separator + "gdelt" + File.separator + "event";
            exportFileDqFolderPath = eventFileFolderPath + "_dq" + File.separator + "gdelt" + File.separator + "event";
        }
        exportFileList = new ArrayList<>();
        formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        yesterdayDateTime = LocalDateTime.now(ZoneId.of("Etc/UTC")).minusDays(1);
        checkCollectDatetimeLimit = LocalDateTime.now(ZoneId.of("Etc/UTC")).minusMinutes(15);
        doCollect = false;
        isFakeDownload = false;
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {

        if (url.contains("-translation")) {
            eventFileName = EVENT_TRANS_FILE_NAME;
            tempFilePath = ispider4Home + File.separator + TEMP_TRANS_FILE_NAME;
            chkDownloadFilePattern = "00.translation.export.CSV.zip";
        }
        if (url.contains("?")) {    /* url에서 파라미터를 추출한다. */
            String[] urlArr = url.split("\\?");
            if (urlArr.length == 2) {
                String paramsStr = urlArr[1];
                String[] paramStrArr = paramsStr.split("&");
                for (String paramStr : paramStrArr) {
                    String[] param = paramStr.split("=");
                    String key = param[0];
                    String value = param[1];
                    if ("date".equals(key)) {   /* 특정 날짜 전까지, 또는 특정 기간 내의 데이터만 수집하도록 처리하는 파라미터 */
                        if (value.contains("_")) {
                            String[] dateArr = value.split("_");
                            String startDate = dateArr[0];
                            String endDate = dateArr[1];
                            checkCollectStartDateLimit = LocalDateTime.parse(startDate, formatter);
                            checkCollectEndDateLimit = LocalDateTime.parse(endDate, formatter);
                        } else {
                            checkCollectStartDateLimit = LocalDateTime.parse(value, formatter);
                        }
                    } else if ("isFakeDownload".equals(key)) {
                        if (value.equals("true")) {
                            isFakeDownload = true;
                            if (!connectionUtil.isLocal()) {    /* 이 조건문은 isFakeDownload 초기화보다 밑에 꼭 둘 것, 위에서 isFakeDownload를 뭘로 설정하든 로컬이 아니면 무조건 false로 만든다. */
                                isFakeDownload = false;
                            }
                        }
                    }
                }
            }
        }

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {

        BufferedWriter writer = null;
        ReversedLinesFileReader reversedReader = null;
        File tempFile = null;
        LocalDateTime lastCollectDate = eventDateFileReader();
        LocalDateTime checkCollectDate = null;
        if (checkCollectStartDateLimit != null) {
            checkCollectDate = checkCollectStartDateLimit;
        } else {
            checkCollectDate = checkCollectDatetimeLimit;
        }

        try {
            writer = new BufferedWriter(new FileWriter(tempFilePath));  // 파일로 저장
            writer.write(htmlSrc);
            tempFile = new File(tempFilePath);
            reversedReader = new ReversedLinesFileReader(tempFile, StandardCharsets.UTF_8);   // 읽는 순서를 역순으로 뒤집기
            String zipUrl = "";
            int i = 0;
            if (lastCollectDate == null || checkCollectDate.isAfter(lastCollectDate) || checkCollectStartDateLimit != null) {
                boolean firstDownload = true;
                if (checkCollectStartDateLimit != null) {
                    lastCollectDate = checkCollectStartDateLimit;
                }
                if (lastCollectDate == null) {
                    lastCollectDate = yesterdayDateTime;
                }
                String line;
                while ((line = reversedReader.readLine()) != null) {
                    if (!line.equals("") && line.contains(chkDownloadFilePattern)) {
                        String[] fileInfos = line.split(" ");
                        if (fileInfos.length == 3) {
                            String fileUrl = fileInfos[2];
                            String fileDatetimeStr = fileUrl.substring(fileUrl.lastIndexOf("/") + 1, fileUrl.lastIndexOf("/") + 15);
                            LocalDateTime fileDatetime = LocalDateTime.parse(fileDatetimeStr, formatter);
                            if (
                                (checkCollectEndDateLimit == null && fileDatetime.isAfter(lastCollectDate))
                                || (checkCollectEndDateLimit != null && fileDatetime.isAfter(lastCollectDate) && checkCollectEndDateLimit.isAfter(fileDatetime))
                            ) {
                                int fileSize = Integer.parseInt(fileInfos[0]);
                                String[] fileNameArr = fileUrl.split("/");
                                String fileName = fileNameArr[fileNameArr.length - 1];
                                if (isFakeDownload) {
                                    System.out.println("Download File Success: " + fileName);
                                    scsCnt++;
                                } else {
                                    downloadFile(fileUrl, exportFileFolderPath, fileName);  // zip파일 다운로드
                                    copyFile(exportFileFolderPath, exportFileDqFolderPath, fileName);   // 다운로드한 파일 transfer_dq 폴더로 복사
                                    connectionUtil.makeFinFile(exportFileFolderPath, fileName);     // FIN 파일 생성 (transfer)
                                    connectionUtil.makeFinFile(exportFileDqFolderPath, fileName);   // FIN 파일 생성 (transfer_dq)
                                    Path filePath = Paths.get(exportFileFolderPath + "/" + fileName);
                                    File chkFile = filePath.toFile();
                                    if (filePath != null && chkFile.exists()) {
                                        long bytes = Files.size(filePath);
                                        if (bytes == fileSize) {    // 파일 다운로드 검증
                                            exportFileList.add(fileName);
                                            if ((checkCollectEndDateLimit == null || "".equals(checkCollectEndDateLimit)) && !isFakeDownload && firstDownload) {   /* 특정 기간 내의 파일을 다운로드 받을 때는 최신 파일 데이터 기록을 하지 않는다. */
                                                String fileNameDate = fileName.substring(0, 14);
                                                System.out.println(fileName + ": 해당 파일 날짜로 기록을 저장했습니다. date 파라미터가 없을 경우 이 파일 시간 이후의 파일을 다운로드합니다.");
                                                eventDateFileWriter(fileNameDate);
                                                doCollect = true;
                                                firstDownload = false;
                                            }
                                            System.out.println("Download File Success: " + fileName);
                                            scsCnt++;
                                        } else {
                                            System.out.println("Download File Failed: " + fileName + " 해당 파일은 정상적으로 다운로드하지 못했습니다.");
                                            chkFile.delete();
                                            failrCnt++;
                                        }
                                    } else {
                                        System.out.println("Download File Failed: " + fileName + " 해당 파일은 정상적으로 다운로드하지 못했습니다.");
                                        if (chkFile.exists()) {
                                            chkFile.delete();
                                        }
                                        failrCnt++;
                                    }
                                }
                            } else {
                                if ((checkCollectEndDateLimit == null || "".equals(checkCollectEndDateLimit))) {    /* date 파라미터에서 checkCollectEndDateLimit 값이 지정될 경우 파일 다운로드 조건에서 벗어났다고 체크를 중단하지 않는다. */
                                    System.out.println("이미 최신 export 파일의 수집이 완료되었습니다. event export 파일 체크를 완료합니다.");
                                    break;
                                } else if (lastCollectDate.isAfter(fileDatetime)) {
                                    System.out.println("파라미터에 입력된 date까지의 값을 모두 체크하여 event export 파일 체크를 완료합니다.");
                                    break;
                                }
                            }
                        }
                    }
                }
            } else {
                System.out.println("이미 최신 export 파일의 수집이 완료되었습니다.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            htmlSrc = "<attachfile></attachfile>\n<title></title>\n<url>url</url>";

            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (reversedReader != null) {
                try {
                    reversedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        return htmlSrc;
    }

    @Override
    public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
        List<String> urls = new ArrayList<String>();
        urls.add(naviUrl);
        return urls;
    }

    @Override
    public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
        for (int i = 0; i < row.size(); i++) {
//            String nodeId = row.getNodeByIdx(i).getId();
            String nodeName = row.getNodeByIdx(i).getName();
            String nodeValue = row.getNodeByIdx(i).getValue();

            if (nodeName.equals("cl_cd")) {
                cl_cd = nodeValue;
            } else if (nodeName.equals("origin_cd")) {
                origin_cd = nodeValue;
            }
        }
    }

    @Override
    public boolean validData(Row row, DqPageInfo dqPageInfo) {
        boolean isCheck = false;
        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            List<String> fileNames = new ArrayList<>();
            File file = new File(exportFileFolderPath);
            for (String fileName : exportFileList) {    // 파일목록 체크 및 FIN파일 생성
                if (fileName.contains(chkDownloadFilePattern)) {
                    fileNames.add(fileName);
                }
            }
            if (!connectionUtil.isLocal() && !isTest && doCollect) {    // 또한 로컬 및 테스트 환경이 아닌 상태에서 수집이 일어났을 떄에만 로그를 남긴다.
                connectionUtil.makeGdeltCollectLog(cl_cd, origin_cd, scsCnt, failrCnt); // 수집로그 저장
            } else {
                connectionUtil.printGdeltCollectLog(scsCnt, failrCnt); // 수집로그 저장
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== " + extensionName + " End ===");
        }
    }

    /**
     * URL 파일 다운로드
     *
     * @param fileUrl
     * @param filePath
     * @param fileName
     */
    private void downloadFile(String fileUrl, String downloadDirectoryPath, String fileName) {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
        File dir = new File(downloadDirectoryPath);
        if (!dir.exists()) {
            System.out.println(downloadDirectoryPath + ": 디렉토리가 존재하지 않습니다!!");
            dir.mkdirs();
            System.out.println(downloadDirectoryPath + ": 디렉토리를 생성했습니다.");
        }

        HttpURLConnection con = null;
        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            String filePath = downloadDirectoryPath + File.separator + fileName;
            File dfile = new File(filePath);
            if (dfile.exists()) {
                System.out.println("이미 존재하는 파일입니다. : " + filePath);
            } else {
                URL downloadUrl = new URL(fileUrl);
                con = (HttpURLConnection) downloadUrl.openConnection(proxy);
                rbc = Channels.newChannel(con.getInputStream());
                fos = new FileOutputStream(filePath);
                System.out.println("Download File > fileName : " + filePath);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }
        } catch (IOException e) {
            System.out.println("파일 다운로드 중 에러 발생: " + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (rbc != null) {
                try {
                    rbc.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (con != null) {
                con.disconnect();
            }
        }
    }

    /**
     * 파일 복사
     *
     * @param sourceFileDirPath 복사할 파일의 경로
     * @param destinationFileDirPath    복사될 파일의 경로
     * @param fileName  복사 대상 파일명
     */
    private void copyFile(String sourceFileDirPath, String destinationFileDirPath, String fileName) {
        File destinationDir = new File(destinationFileDirPath);
        if (!destinationDir.exists()) {
            System.out.println(destinationFileDirPath + ": 디렉토리가 존재하지 않습니다!!");
            destinationDir.mkdirs();
            System.out.println(destinationFileDirPath + ": 디렉토리를 생성했습니다.");
        }
        String sourceFile = sourceFileDirPath + File.separator + fileName;
        String destinationFile = destinationFileDirPath + File.separator + fileName;
        Path sourcePath = Paths.get(sourceFile);
        Path destinationPath = Paths.get(destinationFile);
        if (sourcePath.toFile().exists()) {
            try {
                Files.copy(sourcePath, destinationPath);    // Files 클래스의 copy 메서드를 사용하여 파일 복사
                System.out.println(fileName + " 파일이 " + destinationFileDirPath + " 위치로 성공적으로 복사되었습니다.");
            } catch (IOException e) {
                System.err.println(fileName + "파일의 " + destinationFileDirPath + " 경로 복사 중 오류가 발생했습니다: " + e.getMessage());
            }
        } else {
            System.out.println(fileName + " 파일이 " + destinationFileDirPath + " 위치에 존재하지 않습니다.");
        }
    }

    /**
     * 날짜 정보 파일 읽어오기
     *
     * @return
     */
    private LocalDateTime eventDateFileReader() {

        File eventDateFile = new File(eventFileFolderPath + File.separator + eventFileName);
        String lastColctDate = "";
        LocalDateTime localDateTime = null;
        try {
            if (eventDateFile.exists()) {
                Scanner scan = new Scanner(eventDateFile);
                while (scan.hasNextLine()) {
                    lastColctDate += scan.nextLine();
                }
                localDateTime = LocalDateTime.parse(lastColctDate, formatter);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            return localDateTime;
        }
    }

    /**
     * 날짜 정보 파일에 쓰기
     *
     * @param targetDate
     */
    private void eventDateFileWriter(String targetDate) {
        File eventFileFolder = new File(eventFileFolderPath);
        if (!eventFileFolder.exists()) {   // 경로가 없다면 생성합니다. (디렉토리)
            try {
                eventFileFolder.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(eventFileFolderPath + File.separator + eventFileName, false);
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