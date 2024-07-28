package org.fisheep;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {
  /*  public static void main(String[] args) throws IOException {

        String command = "/root/soar -query \'select * from dual\'";
        //接收正常结果流
        ByteArrayOutputStream susStream = new ByteArrayOutputStream();
        //接收异常结果流
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        CommandLine commandLine = CommandLine.parse(command);
        DefaultExecutor exec = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(susStream, errStream);
        exec.setStreamHandler(streamHandler);
        int code = exec.execute(commandLine);
        System.out.println("退出代码: " + code);
        System.out.println(susStream.toString("UTF8"));
        System.out.println(errStream.toString("UTF8"));

    }*/

    public static void main(String[] args) throws IOException {

        var processBuilder = new ProcessBuilder();

        processBuilder.command("/root/soar", "-query", "select * from dual");
        //processBuilder.command("ipconfig", "/all");

        var process = processBuilder.start();

        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

        }
    }
}
