package com.github.kirer.server.plugin;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import com.github.kirer.server.screenshot.ScreenShot;
import dalvik.system.DexClassLoader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 插件系统实现
 * 复刻自 com.autogo.Plugin.Plugin
 * 支持动态APK加载、实例创建和方法调用
 */
public class Plugin {
    private static Map<String, DexClassLoader> classLoaderMap = new ConcurrentHashMap<>();
    private static Map<String, Object> objectMap = new ConcurrentHashMap<>();
    private static Map<String, AssetManager> assetMap = new ConcurrentHashMap<>();
    private static Map<String, Bitmap> bitmapCacheMap = new ConcurrentHashMap<>();
    
    // 当前工作路径，用于存放临时文件
    private static String workingPath;
    
    /**
     * 设置工作路径
     */
    public static void setWorkingPath(String path) {
        workingPath = path;
    }
    
    /**
     * 处理插件命令
     * @param writer 输出流
     * @param params 命令参数
     * @throws IOException IO异常
     */
    public static void call(BufferedWriter writer, String[] params) throws IOException {
        if (params.length < 3) {
            writer("ERROR", "Invalid parameters", writer);
            return;
        }
        
        String requestId = params[1];
        String command = params[2];
        
        try {
            switch (command) {
                case "loadApk":
                    if (params.length >= 4) {
                        String result = loadApk(params[3]);
                        writer(requestId, result, writer);
                    } else {
                        writer(requestId, "ERROR: Missing APK path", writer);
                    }
                    break;
                    
                case "newInstance":
                    if (params.length >= 6) {
                        String result = newInstance(params[3], params[4], params[5]);
                        writer(requestId, result, writer);
                    } else {
                        writer(requestId, "ERROR: Missing parameters for newInstance", writer);
                    }
                    break;
                    
                case "call":
                    if (params.length >= 6) {
                        String result = call(params[3], params[4], params[5]);
                        writer(requestId, result, writer);
                    } else {
                        writer(requestId, "ERROR: Missing parameters for call", writer);
                    }
                    break;
                    
                default:
                    writer(requestId, "ERROR: Unknown command: " + command, writer);
                    break;
            }
        } catch (Exception e) {
            writer(requestId, "ERROR: " + e.getMessage(), writer);
        }
    }
    
    /**
     * 写入响应数据
     */
    private static void writer(String requestId, String data, BufferedWriter writer) throws IOException {
        try {
            byte[] dataBytes = data.getBytes("UTF-8");
            String response = requestId + String.format("%06d", dataBytes.length) + data;
            writer.write(response);
            writer.flush();
        } catch (IOException e) {
            System.err.println("Failed to write response: " + e.getMessage());
        }
    }
    
    /**
     * 加载APK文件
     * @param apkPath APK文件路径
     * @return ClassLoader的字符串标识
     * @throws IOException IO异常
     */
    public static String loadApk(String apkPath) throws IOException {
        if (workingPath == null) {
            throw new IllegalStateException("Working path not set");
        }
        
        DexClassLoader classLoader = new DexClassLoader(
            apkPath, 
            workingPath, 
            workingPath, 
            Plugin.class.getClassLoader()
        );
        
        String loaderId = classLoader.toString();
        if (loaderId.contains("DexPathList[[],")) {
            return ""; // 加载失败
        }
        
        // 释放SO库文件
        releaseSO(apkPath);
        
        classLoaderMap.put(loaderId, classLoader);
        return loaderId;
    }
    
    /**
     * 创建类实例
     * @param loaderId ClassLoader标识
     * @param className 类名
     * @param params 构造函数参数
     * @return 实例UUID
     */
    public static String newInstance(String loaderId, String className, String params) 
            throws IllegalAccessException, InstantiationException, IllegalArgumentException, InvocationTargetException {
        try {
            DexClassLoader classLoader = classLoaderMap.get(loaderId);
            if (classLoader == null) {
                throw new IllegalArgumentException("ClassLoader not found: " + loaderId);
            }
            
            Class<?> clazz = classLoader.loadClass(className);
            
            Class<?>[] paramTypes;
            Object[] paramValues;
            Object instance;
            
            if ("null".equals(params)) {
                paramTypes = null;
                paramValues = null;
            } else {
                Object[] paramData = listFormat(params);
                paramTypes = (Class<?>[]) paramData[0];
                paramValues = (Object[]) paramData[1];
            }
            
            if (paramTypes != null) {
                instance = clazz.getDeclaredConstructor(paramTypes).newInstance(paramValues);
            } else {
                instance = clazz.getDeclaredConstructor().newInstance();
            }
            
            String instanceId = UUID.randomUUID().toString();
            objectMap.put(instanceId, instance);
            
            // 清理Bitmap资源
            if (paramValues != null) {
                for (Object param : paramValues) {
                    if (param instanceof Bitmap) {
                        Bitmap bitmap = (Bitmap) param;
                        if (!bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    }
                }
            }
            
            return instanceId;
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | 
                 NoSuchMethodException | InvocationTargetException e) {
            System.err.println("Failed to create instance: " + e.getMessage());
            throw new RuntimeException("Instance creation failed", e);
        }
    }

    /**
     * 调用对象方法
     * @param instanceId 实例UUID
     * @param methodName 方法名
     * @param params 方法参数
     * @return 方法返回值的字符串表示
     */
    public static String call(String instanceId, String methodName, String params)
            throws IllegalAccessException, NoSuchMethodException, SecurityException,
                   IllegalArgumentException, InvocationTargetException {
        try {
            Object instance = objectMap.get(instanceId);
            if (instance == null) {
                throw new IllegalArgumentException("Instance not found: " + instanceId);
            }

            Class<?> clazz = instance.getClass();
            Method method;
            Object[] paramValues;

            if (!"null".equals(params)) {
                Object[] paramData = listFormat(params);
                Class<?>[] paramTypes = (Class<?>[]) paramData[0];
                paramValues = (Object[]) paramData[1];
                method = clazz.getMethod(methodName, paramTypes);
            } else {
                method = clazz.getMethod(methodName);
                paramValues = null;
            }

            Object result = method.invoke(instance, paramValues);

            // 清理Bitmap资源
            if (paramValues != null) {
                for (Object param : paramValues) {
                    if (param instanceof Bitmap) {
                        Bitmap bitmap = (Bitmap) param;
                        if (!bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    }
                }
            }

            if (result != null) {
                return convertToString(result);
            }
            return "";
        } catch (Exception e) {
            System.err.println("Method call failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 将对象转换为字符串表示
     */
    public static String convertToString(Object obj) {
        Class<?> clazz = obj.getClass();
        if (clazz.isPrimitive() || isWrapperType(clazz) || (obj instanceof String)) {
            return obj.toString();
        }

        if (clazz.isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                sb.append(convertToString(Array.get(obj, i)));
                if (i < length - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }

        try {
            return tryToString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    /**
     * 尝试使用反射获取对象的字符串表示
     */
    private static String tryToString(Object obj) throws IllegalAccessException, NoSuchMethodException {
        if (!obj.getClass().getMethod("toString").getDeclaringClass().equals(Object.class)) {
            return obj.toString();
        }

        StringBuilder sb = new StringBuilder("{");
        Field[] fields = obj.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            field.setAccessible(true);
            Object value = field.get(obj);
            sb.append(field.getName());
            sb.append("=");
            sb.append(convertToString(value));
            if (i < fields.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 检查是否为包装类型
     */
    private static boolean isWrapperType(Class<?> clazz) {
        return clazz == Boolean.class || clazz == Byte.class || clazz == Character.class ||
               clazz == Short.class || clazz == Integer.class || clazz == Long.class ||
               clazz == Float.class || clazz == Double.class || clazz == Void.class ||
               clazz == Bitmap.class;
    }

    /**
     * 解析参数列表格式
     * @param paramString 参数字符串
     * @return 包含参数类型和值的数组
     */
    private static Object[] listFormat(String paramString) {
        String[] parts = paramString.split("@@");
        int paramCount = parts.length / 2;
        Object[] values = new Object[paramCount];
        Class<?>[] types = new Class[paramCount];

        int valueIndex = 0;
        for (int i = 0; i < parts.length; i += 2) {
            String type = parts[i];
            String value = parts[i + 1];

            switch (type) {
                case "int":
                    values[valueIndex] = Integer.valueOf(Integer.parseInt(value));
                    types[valueIndex] = Integer.TYPE;
                    break;
                case "long":
                    values[valueIndex] = Long.valueOf(Long.parseLong(value));
                    types[valueIndex] = Long.TYPE;
                    break;
                case "float":
                    values[valueIndex] = Float.valueOf(Float.parseFloat(value));
                    types[valueIndex] = Float.TYPE;
                    break;
                case "double":
                    values[valueIndex] = Double.valueOf(Double.parseDouble(value));
                    types[valueIndex] = Double.TYPE;
                    break;
                case "boolean":
                    values[valueIndex] = Boolean.valueOf(Boolean.parseBoolean(value));
                    types[valueIndex] = Boolean.TYPE;
                    break;
                case "string":
                    if ("null".equals(value)) {
                        value = "";
                    }
                    values[valueIndex] = value;
                    types[valueIndex] = String.class;
                    break;
                case "bitmap":
                    String[] coords = value.split(",");
                    try {
                        values[valueIndex] = ScreenShot.getBitmap(
                            Integer.parseInt(coords[0]),
                            Integer.parseInt(coords[1]),
                            Integer.parseInt(coords[2]),
                            Integer.parseInt(coords[3])
                        );
                        types[valueIndex] = Bitmap.class;
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Failed to get bitmap", e);
                    }
                    break;
                case "bitmappath":
                    Bitmap cachedBitmap = bitmapCacheMap.get(value);
                    if (cachedBitmap == null) {
                        cachedBitmap = BitmapFactory.decodeFile(value);
                        if (cachedBitmap != null) {
                            bitmapCacheMap.put(value, cachedBitmap);
                        }
                    }
                    values[valueIndex] = cachedBitmap;
                    types[valueIndex] = Bitmap.class;
                    break;
                case "bitmapbase64":
                    byte[] decodedBytes = Base64.decode(value, 0);
                    values[valueIndex] = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                    types[valueIndex] = Bitmap.class;
                    break;
                case "assetManager":
                    AssetManager cachedAssetManager = assetMap.get(value);
                    if (cachedAssetManager == null) {
                        try {
                            cachedAssetManager = AssetManager.class.newInstance();
                            AssetManager.class.getMethod("addAssetPath", String.class)
                                .invoke(cachedAssetManager, value);
                            assetMap.put(value, cachedAssetManager);
                        } catch (Exception e) {
                            System.err.println("Failed to create AssetManager: " + e.getMessage());
                            throw new RuntimeException("AssetManager creation failed", e);
                        }
                    }
                    values[valueIndex] = cachedAssetManager;
                    types[valueIndex] = AssetManager.class;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported type: " + type);
            }
            valueIndex++;
        }

        return new Object[]{types, values};
    }

    /**
     * 从APK中释放SO库文件
     * @param apkPath APK文件路径
     * @return 是否成功
     * @throws IOException IO异常
     */
    private static boolean releaseSO(String apkPath) throws IOException {
        String arch = System.getProperty("os.arch");
        String libPath = arch != null && arch.contains("x86_64") ? "lib/x86_64/" : "lib/arm64-v8a/";

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith(libPath) && entryName.endsWith(".so")) {
                    String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    File outputFile = new File(workingPath, fileName);

                    try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            System.err.println("Failed to release SO libraries: " + e.getMessage());
            throw e;
        }
    }
}
