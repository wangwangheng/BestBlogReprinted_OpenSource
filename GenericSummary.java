import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;

public class GenericSummary {
    private static String rootPath;
    private static File rootFile;
    public static void main(String[] args) throws IOException {
        if(args == null || args.length < 1){
            return;
        }
        //        rootFile = new File("/Users/wangheng/Documents/笔记");
        rootFile = new File(args[0]);
        rootPath = rootFile.getAbsolutePath();
        File summaryFile = new File(rootFile,"SUMMARY.md");
        
        if(summaryFile.exists()){
            summaryFile.delete();
        }
        summaryFile.createNewFile();
        
        String preStr = "# Summary\n";
        String str = preStr + getSummaryString(rootFile);
        
        FileOutputStream fos = new FileOutputStream(summaryFile);
        fos.write(str.getBytes());
        fos.close();
        System.out.println(str);
    }
    
    private static final String getSummaryString(File dir) throws IOException{
        if(dir == null || !dir.exists() || (dir.isHidden() && dir != rootFile)){
            return null;
        }
        String absolutePath = dir.getAbsolutePath();
        String path = absolutePath.substring(absolutePath.indexOf(rootPath) + rootPath.length());
        int countDir = path.split("/").length - 2;
        if(path.indexOf(dir.getName()) > 0){
            path = path.substring(1, path.indexOf(dir.getName()));
        }
        
        String space = "";
        for(int i = 0;i < countDir;i++){
            space = space + "   ";
        }
        if(dir.isFile()){
            return space + "* [" + dir.getName().substring(0, dir.getName().lastIndexOf(".")) + "](" + path +  dir.getName() + ")\n";
        }else{
            StringBuffer sb = new StringBuffer();
            File subDirOrMdFile[] = dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isDirectory() || file.getName().endsWith(".md");
                }
            });
            if(rootFile != dir && subDirOrMdFile != null && subDirOrMdFile.length > 0){
                File readme = new File(dir,"README.md");
                if(!readme.exists()){
                    readme.createNewFile();
                    FileOutputStream fos = null;
                    try{
                        fos = new FileOutputStream(readme);
                        fos.write(("# " + dir.getName() + "\n").getBytes());
                        fos.flush();
                    }catch(Exception e){
                        e.printStackTrace();
                    }finally{
                        if(fos != null){
                            fos.close();
                        }
                    }
                }
                
                
                sb.append(space + "* [" + dir.getName() + "](" + path + dir.getName() + File.separator + "README.md)\n");
            }
            File files[] = dir.listFiles();
            for(File f : files){
                String fileName = f.getName();
                if(".DS_Store".equals(fileName)
                   || "README.md".equals(fileName)
                   || "SUMMARY.md".equals(fileName)
                   || "_book".equals(fileName)
                   || "源代码".equals(fileName)){
                    continue;
                }
                if(f.isFile() && !fileName.endsWith(".md")){
                    continue;
                }
                String str = getSummaryString(f);
                sb.append(str == null ? "" : str);
            }
            
            return sb.toString();
        }
        
    }
    
    
}
