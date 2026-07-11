package vip.xiaonuo.fscloud.core.base;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class AZipProcessor<T> {

    /**
     * 可能是一个文件，也可能是一个集合
     */
    T src;
    /**
     * 通常是一个自拍文件
     */
    String targetFile;
    /**
     * 是否包含完整路径
     */
    boolean includePath=false;


    public AZipProcessor(T src, String targetFile, boolean includePath) {
        this.src = src;
        this.targetFile = targetFile;
        this.includePath = includePath;
    }

    public AZipProcessor(T src, String targetFile) {
        this.src = src;
        this.targetFile = targetFile;
        this.includePath = false;
    }

    /**
     * 构建押许哦文件的主程序
     * @throws IOException
     */
    public void zip() throws IOException {
        FileOutputStream fos = new FileOutputStream(targetFile);
        try (fos; ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipProcess(zos);
        }
    }

    /**
     * 分块读取文件内容 到 zip 输出中
     * @param fis 输入
     * @param zos 输出
     * @throws IOException
     */
    protected void readData(FileInputStream fis, ZipOutputStream zos) throws IOException {
        byte[] bytes = new byte[1024];
        int length;
        while((length = fis.read(bytes)) >= 0) {
            zos.write(bytes, 0, length);
        }
    }

    /**
     * 压缩一个指定的单一文件到指定的entry中
     * @param srcFile
     * @param entryName
     * @param zipOut
     * @throws IOException
     */
    protected void zipSingleFile(File srcFile, String entryName, ZipOutputStream zipOut) throws IOException{
        if(srcFile.exists() && srcFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(srcFile)) {
                ZipEntry zipEntry = new ZipEntry(entryName);
                zipOut.putNextEntry(zipEntry);
                readData(fis, zipOut);
            }
        }
    }

    /**
     * 简单压缩只包含文件名
     * @param srcFile
     * @param zipOut
     * @throws IOException
     */
    protected void zipSingleFile(File srcFile, ZipOutputStream zipOut) throws IOException{
        zipSingleFile(srcFile, includePath? srcFile.getAbsolutePath():srcFile.getName(), zipOut);
    }

    /**
     * 处理具体的压缩资源进行压缩， 通常是 单个文件或者一个集合
     */
    abstract void zipProcess(ZipOutputStream zipOut) throws IOException;

}
