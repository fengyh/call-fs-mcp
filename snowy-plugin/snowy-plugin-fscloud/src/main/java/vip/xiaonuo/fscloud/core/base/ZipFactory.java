package vip.xiaonuo.fscloud.core.base;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 静态工程类, 通用调用接口
 */
public class ZipFactory {

    /**
     * 处理指定path， 不论目录还是文件, 打包不包含完整目录
     * @param path
     * @param targetFile
     * @return
     * @throws FileNotFoundException
     */
    public static AZipProcessor<?> build(String path, String targetFile) throws FileNotFoundException {
        return build(path, targetFile, false);
    }


    /**
     * 处理指定path， 不论目录还是文件，包内包含完整的绝对路径
     * @param path
     * @param targetFile
     * @param includePath
     * @return
     * @throws FileNotFoundException
     */

    public static AZipProcessor<?> build(String path, String targetFile, boolean includePath) throws FileNotFoundException {
        File file = new File(path);
        if(file.exists()) {
            return file.isFile() ?
                    new ZipSingleFile(path, targetFile, includePath) :
                    new ZipPath(path, targetFile, includePath);
        } else {
            throw  new FileNotFoundException();
        }
    }

    /**
     * 默认解压程序，将但钱文件包的内容解压到当前目录中
     * @param zipFilePath
     * @return
     * @throws IOException
     */
    public static boolean unzip(Path zipFilePath)  throws IOException {
        try (
                FileInputStream fis = new FileInputStream(zipFilePath.toFile());
                ZipInputStream zis = new ZipInputStream (fis);
        ) {
            ZipEntry zipEntry = zis.getNextEntry();
            byte[] buffer = new byte[1024];
            while (zipEntry!=null) {
                Path zPath = zipFilePath.getParent().resolve(zipEntry.getName());
                Files.createDirectories(zPath.getParent());
                try(FileOutputStream fos = new FileOutputStream (zPath.toFile())) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0,  len);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
        return true;
    }


}
