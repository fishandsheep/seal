<h4 align="right"><strong>简体中文</strong> | <a href="https://github.com/fishandsheep/seal/blob/master/README.md">English</a>
<p align="center">
    <img src=https://github.com/fishandsheep/seal/blob/master/src/main/resources/public/static/img/seal.svg width=138/>
</p>
<p align="center"><strong>A tool to find risky SQL by parsing tcpdump files, only
            supports  <em>MySQL 8.x</em>.</strong></p>
<div align="center">

</div>

## 介绍
一个通过解析tcpdump文件，分析sql风险的工具。

## 快速开始

### 准备
1. 服务器下载[soar](https://github.com/fishandsheep/soar/releases/download/1.0/soar),并记住文件目录,例如 `soar.path=/root/soar`
    ```
    wget https://github.com/fishandsheep/soar/releases/download/1.0/soar
    chmod +x soar
    ```
2. `jdbcurl`上添加`useSSL=false`参数(使用了SS加密,无法解析`tcpdump`下来的文件)

3. 使用`tcpdump`命令获取网络抓包文件

    `tcpdump`文件是通过linux的`tcpdump`命令生成标准的`.pcap`文件，通过解析二进制文件获取sql。
    
    `tcpdump`命令需要`root`或 `sudo`权限，例如：
    
    ```shell
    sudo tcpdump -i eth0 -s 0 -w mysql.pcap 'port 3306'
    ```
    
    `-i eth0` : 监控 eth0 网卡
    
    `-s 0` : 可选，兼容旧版本 tcpdump
    
    `-w mysql.pcap`: 保存的文件名称
    
    `port 3306` : 指定通过3306端口的数据，**包含目标端口和源端口，包含源端口为了获取sql的耗时**

### 启动项目
1. 获取项目，需要`java version >= 17`
   
    - 方式一：直接下载发布的jar包
       
    - 方式二：手动编译jar包
       ```
       git clone https://github.com/fishandsheep/seal.git
       cd seal
       mvn clean package
       ```
3. 启动项目增加`-Dsoar.path=/root/soar`参数
   ```
   nuhup java -Dsoar.path=/root/soar  -jar seal-1.0-SNAPSHOT.jar &
   ```
4. 访问 `http://ip:7070/seal`

### 如何使用
1. 创建数据库连接
   <img src=image/connectdb.png/>
2. 解析上传的`.pcap`文件
   <img src=image/parse.png/>
3. 查看解析的sql,获取sql风险得分`Score`、sql执行的次数`Count`、sql最长的执行时间`Max Time`
   <img src=image/risksql.png/>
4. 点击风险得分，查看sql风险详情、sql优化建议、sql执行计划解读(若数据库能正常连接) 
    <img src=image/riskinfo.png/>
### 演示视频
TODO

## 特别感谢
| 框架            | 技术           | 官网                       |
|---------------|--------------|--------------------------|
| css           | hyperui      | https://www.hyperui.dev/ |
| js            | alpine.js    | https://alpinejs.dev/    |
| web           | javalin      | https://javalin.io/      |
| database      | eclipsestore | https://eclipsestore.io/ |
| parse tcpdump | kaitai       | https://doc.kaitai.io/   |
