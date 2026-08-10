# Latex文档示范工程使用说明

本示范工程可以作为写latex文档的模板。README.md讲述如何使用本模板。

### Latex文档写作环境搭建

> 搜索关键字`vscode latex`，找几篇安装texlive、vscode、sumatrapdf安装的文章参考即可。例如：
>
> - [VScode+latex+Sumatra PDF环境配置（步步到位）](https://blog.csdn.net/chris_dua/article/details/124365821)
> - [使用VSCode编写LaTeX](https://zhuanlan.zhihu.com/p/38178015)

### 本示范工程结构说明

```
.
├── README.md
├── mystyle.sty
├── out
│   └── uml
│       ├── demoapp-flow
│       │   └── app-flow.latex
│       ├── rcs-sm
│       │   ├── rcs-sm.latex
│       │   └── rcs-sm.png
│       └── uc-presetting-call-material
│           └── presetting-call-material.latex
├── pics
│   ├── bootstrap-mp4-files.jpg
│   ├── dev-log
│   │   ├── typescript-vue-for-home-vue.jpg
│   │   ├── vue-typescript-app-configuration.png
│   │   ├── vue-typescript-app-result.jpg
│   │   ├── vue3-composition-api.jpg
│   │   └── vue3-create-selection.png
│   ├── main-framework.png
│   ├── sample-workfiles.png
│   ├── sm-example-1.jpg
│   ├── start-spring-io.jpg
│   ├── vue-create-result.jpg
│   ├── vue-structure-1.jpg
│   ├── vue-structure.jpg
│   └── vuejs-init-index.jpg
├── reference.bib
├── sample-doc.tex
├── sections
│   ├── appendix1.tex
│   ├── appendix2.tex
│   ├── appendix3.tex
│   ├── appendix4.tex
│   ├── brief.tex
│   ├── framework.tex
│   └── statemachine.tex
└── uml
    ├── demoapp-flow.wsd
    ├── rcs-sm.wsd
    └── uc-presetting-call-material.wsd

9 directories, 32 files
```

- README.md - 本文
- mystyle.sty - 定义颜色，方便文章中使用，比如该文件中这个`lightseagreen`（淡海绿色）的定义可在任何tex文件中这样是使用：`\textcolor{lightseagreen}`
- 【out】- 文件夹，是【uml】文件夹的导出文件，可以是latex格式的图形文件，也可以是png图形文件
- 【pics】- 图片文件夹
- reference.bib - 文献说明，具体格式可参照文章[latex参考文献bib基本格式_LaTeX 中的参考文献(.bib) BibTex 用法](https://blog.csdn.net/weixin_36048838/article/details/112013156)

- sample-doc.tex - 本工程的主文件
- 【sections】- 本工程的章节文件文件夹
- 【uml】- plantuml的uml文件夹，vscode需要安装plantuml插件，电脑需要安装graphviz软件和java运行环境，插件和环境安装无误后，在.wsg文件编辑区的鼠标右键菜单将出现**Preview Current Diagram**，选择它在编辑区的右侧将可预览UML图形，.wsg文件编辑区右键菜单中还可选择Export Current Diagram导出图形至上述【out】文件夹，以便供latex文档使用。关于plantuml画图可参照官网：[https://plantuml.com/zh/](https://plantuml.com/zh/)。

### 最佳实践建议

1. 下载两份示范工程，一份不修改用于参考，一份修改为自己的文档
2. 先修改主文件的文件名，比如`sample-doc.tex` --> `uwdp-dev-guide.tex`
3. 注释主文件中所以（或部分）子章节，编译得到pdf文件
4. 修改主文件的标题、作者、水印、修订记录等，再编译得到pdf文件
5. 以上步骤均顺利后，即可逐渐增加自己的内容
6. 参照示范工程的脚注、跨页长表、超链、图形/子图等内容的实现方法。
7. 参照示范工程的图、表引用，章节引用，文献引用，术语引用方法。
8. 学习plantuml的UML绘图技巧。
9. 对于花了力气、有些不合时宜、又舍不得删除的内容，可放到附录章节，或视不同受众，在主文档中对章节引用做适当注释即可。
10. 考虑到可能需要对外输出，文档单列简介类章节，并注意不要引用翔实啰嗦版的对内章节，这样可轻松灵活按不同需求输出两种版本。
11. latex文档适合协同写作，建议使用gitlab做版本管理
12. 解放了排版的工作，重点可放到内容的质量上，经常使用正向跳转（编辑区文字处*ctrl+alt+j*）和反向跳转（sumatrapdf浏览器中在有问题内容处*双击鼠标*）使得修改/完善/打磨工作更方便。

