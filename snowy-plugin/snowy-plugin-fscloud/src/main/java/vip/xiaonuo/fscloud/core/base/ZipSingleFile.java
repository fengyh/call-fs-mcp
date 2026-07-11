package vip.xiaonuo.fscloud.core.base;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

/**
 * 直接压缩单个文件到指定的压缩文件中
 */
public class ZipSingleFile extends AZipProcessor<String> {
    public ZipSingleFile(String src, String targetFile, boolean includePath) {
        super(src, targetFile, includePath);
    }

    public ZipSingleFile(String src, String targetFile) {
        super(src, targetFile);
    }

    @Override
    void zipProcess(ZipOutputStream zipOut) throws IOException {
        zipSingleFile(new File(src), zipOut);
    }
}
