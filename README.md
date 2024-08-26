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
2. 使用`tcpdump`命令监控数据库

    `tcpdump`文件是通过linux的`tcpdump`命令生成标准的`.pcap`文件，通过解析文件获取相应的sql。
    
    `tcpdump`命令需要`root`或 `sudo`权限，例如执行如下的命令：
    
    ```shell
    tcpdump -i eth0 -s 0 -w mysql.pcap 'port 3306'
    ```
    
    `-i eth0` : 监控 eth0 网卡
    
    `-s 0` : 可选，兼容旧版本 tcpdump
    
    `-w mysql.pcap`: 保存的文件名称
    
    `port 3306` : 指定通过3306端口的数据，**包含目标端口和源端口，包含源端口为了获取sql的耗时**
    
    **注意**：可解析的`.pcap`文件需要的应用`jdbcurl`上存在`useSSL=false`参数
### 启动项目
1. 下载项目，编译
2. 启动项目增加`-Dsoar.path=/root/soar`参数
   ```
   nuhup java -Dsoar.path=/root/soar  -jar seal-1.0-SNAPSHOT.jar &
   ```
3. 访问 `http://xxx.xxx.xxx.xxx:7070/seal`
### 演示视频

## 特别感谢
| 框架            | 技术           | 官网                       |
|---------------|--------------|--------------------------|
| css           | hyperui      | https://www.hyperui.dev/ |
| js            | alpine.js    | https://alpinejs.dev/    |
| web           | javalin      | https://javalin.io/      |
| database      | eclipsestore | https://eclipsestore.io/ |
| parse tcpdump | kaitai       | https://doc.kaitai.io/   |
