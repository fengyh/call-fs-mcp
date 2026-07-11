package vip.xiaonuo.fscloud.core.base;

import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

/**
 * 压缩一个目录中所有文件，压缩包包含文件的目录结构
 */
@Setter
public class ZipPath extends AZipProcessor<String> {

    public ZipPath(String src, String targetFile, boolean includePath) {
        super(src, targetFile, includePath);
    }

    public ZipPath(String src, String targetFile) {
        super(src, targetFile);
    }

    @Override
    void zipProcess(ZipOutputStream zipOut) throws IOException {
        File path = new File(src);
        if(path.exists()) {
            zipFile(path, path.getName(), zipOut); // 开始递归
        }
    }

    /**
     * 递归循环处理所有文件
     * @param currentFile
     * @param fileName
     * @param zipOut
     * @throws IOException
     */
    void  zipFile(File currentFile, String fileName, ZipOutputStream zipOut) throws IOException {
        if(currentFile.isDirectory()) {
            // 递归处理所有的子文件
            File[] children = currentFile.listFiles();
            if(children!=null) {
                for(File child: children) {
                    zipFile(child, fileName + File.separator + child.getName(), zipOut); // 迭代处理
                }
            }
        } else {
            zipSingleFile(
                    currentFile,
                    includePath?
                            currentFile.getAbsolutePath() :
                            currentFile.getAbsolutePath().replace(src.endsWith(File.separator)? src : src + File.separator, ""),
                    zipOut
            );
        }
    }
}
