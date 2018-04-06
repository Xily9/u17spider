import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * 文件存取类
 * Copyright &copy; 2018 Xily.All Rights Reserved.
 * @author Xily
 */

public class FileUtil {

    private static String root="C:\\Users\\Xily\\Desktop\\comic\\";//根文件夹
    private static boolean isCheckDir=false;
    /**
     * 文件保存
     * @param name 文件名
     * @param content 文件内容
     */
    public synchronized static void put(String name,String content) {
        try {
            if(!isCheckDir){
                createDirIfNotExist();
                isCheckDir=true;
            }
            FileWriter fileWriter=new FileWriter(root+name);
            fileWriter.write(content);
            fileWriter.close();
        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println("保存文件"+name+"时发生错误!请重新运行本程序!");
            System.exit(1);
        }
    }
    /**
     * 文件读取
     * @param name 文件名
     * @return 文件内容
     */
    public static String get(String name) {
        try {
            FileReader fileReader=new FileReader(root+name);
            StringBuilder stringBuilder=new StringBuilder();
            int ch;
            while((ch=fileReader.read())!=-1) {
                stringBuilder.append((char)ch);
            }
            fileReader.close();
            return stringBuilder.toString();
        } catch (Exception e) {
            //System.out.println("读取文件"+name+"时发生错误!请重新运行本程序!");
            return null;
        }
    }
    private static void createDirIfNotExist(){
        File f = new File(root);
        if (!f.exists()) {
            f.mkdirs();
        }
    }
}
