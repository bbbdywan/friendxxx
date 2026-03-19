#!/bin/bash

# friendxxx 社交聊天应用启动脚本
# 适用于 2核2G 服务器

APP_NAME="friendxxx"
JAR_NAME="friendxxx.jar"
LOG_DIR="logs"
PID_FILE="${APP_NAME}.pid"

# JVM 参数配置（针对 2G 内存优化）
JVM_OPTS="-Xms1024m \
-Xmx1024m \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof \
-XX:+PrintGCDetails \
-XX:+PrintGCDateStamps \
-Xloggc:${LOG_DIR}/gc.log \
-XX:MetaspaceSize=128m \
-XX:MaxMetaspaceSize=256m \
-Djava.awt.headless=true \
-Dfile.encoding=UTF-8"

# 创建日志目录
mkdir -p ${LOG_DIR}

# 检查是否已经运行
if [ -f "${PID_FILE}" ]; then
    OLD_PID=$(cat ${PID_FILE})
    if ps -p ${OLD_PID} > /dev/null 2>&1; then
        echo "应用已经在运行中 (PID: ${OLD_PID})"
        echo "如需重启，请先执行 ./stop.sh"
        exit 1
    else
        echo "清理旧的 PID 文件"
        rm -f ${PID_FILE}
    fi
fi

# 检查 JAR 文件是否存在
if [ ! -f "${JAR_NAME}" ]; then
    echo "错误: 找不到 ${JAR_NAME}"
    exit 1
fi

# 启动应用
echo "正在启动 ${APP_NAME}..."
nohup java ${JVM_OPTS} -jar ${JAR_NAME} > ${LOG_DIR}/app.log 2>&1 &

# 保存 PID
echo $! > ${PID_FILE}

echo "${APP_NAME} 启动成功 (PID: $(cat ${PID_FILE}))"
echo "日志文件: ${LOG_DIR}/app.log"
echo "查看日志: tail -f ${LOG_DIR}/app.log"
