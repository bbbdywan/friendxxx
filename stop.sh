#!/bin/bash

# friendxxx 社交聊天应用停止脚本

APP_NAME="friendxxx"
PID_FILE="${APP_NAME}.pid"

if [ ! -f "${PID_FILE}" ]; then
    echo "${APP_NAME} 未运行"
    exit 0
fi

PID=$(cat ${PID_FILE})

if ! ps -p ${PID} > /dev/null 2>&1; then
    echo "${APP_NAME} 未运行"
    rm -f ${PID_FILE}
    exit 0
fi

echo "正在停止 ${APP_NAME} (PID: ${PID})..."

# 优雅停机
kill -15 ${PID}

# 等待最多 30 秒
for i in {1..30}; do
    if ! ps -p ${PID} > /dev/null 2>&1; then
        echo "${APP_NAME} 已停止"
        rm -f ${PID_FILE}
        exit 0
    fi
    sleep 1
done

# 强制停止
echo "优雅停机超时，强制停止..."
kill -9 ${PID}
rm -f ${PID_FILE}
echo "${APP_NAME} 已强制停止"
