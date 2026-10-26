#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <pthread.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/uio.h>
#include <math.h>

#define LOG_TAG "DeepPanelRoot"

#define OFFSET_INIT_BASE 0xA342EFC
#define OFFSET_CURRENT_MATCH 0x50
#define OFFSET_GAME 0x4
#define OFFSET_LOCAL_PLAYER 0x94
#define OFFSET_DICT_ENTITIES 0x68
#define OFFSET_MAIN_CAMERA_TRANSFORM 0x28C
#define OFFSET_VIEW_MATRIX 0xE8
#define OFFSET_AIM_ROTATION 0x440
#define OFFSET_PLAYER_IS_DEAD 0x50
#define OFFSET_HEAD 0x49C
#define OFFSET_CHEST 0x4A4
#define OFFSET_ROOT 0x4B0

struct Vector2 { float x, y; };
struct Vector3 { float x, y, z; };

uintptr_t il2cpp_base = 0;
int ff_pid = -1;
JavaVM* g_vm = nullptr;
jobject g_overlayService = nullptr;
jmethodID g_updateEntities = nullptr;
jclass g_entityClass = nullptr;
jmethodID g_entityInit = nullptr;

bool aimbot_on = false;
bool esp_on = false;
int target_bone = 0; 
float aim_fov = 150.0f;
float aim_smooth = 2.0f;

inline bool WorldToScreen(const float m[16], const Vector3& p, int width, int height, Vector2* out) {
    float cx = m[0]*p.x + m[1]*p.y + m[2]*p.z + m[3];
    float cy = m[4]*p.x + m[5]*p.y + m[6]*p.z + m[7];
    float cw = m[12]*p.x + m[13]*p.y + m[14]*p.z + m[15];
    if (cw <= 0.001f) return false;
    out->x = ((cx/cw) + 1.0f) * 0.5f * (float)width;
    out->y = (1.0f - (cy/cw)) * 0.5f * (float)height;
    return true;
}

template<typename T> bool write_mem(uintptr_t addr, T value) {
    if (ff_pid <= 0 || addr < 0x10000) return false;
    struct iovec local[1]; struct iovec remote[1];
    local[0].iov_base = &value; local[0].iov_len = sizeof(T);
    remote[0].iov_base = (void*)addr; remote[0].iov_len = sizeof(T);
    return (process_vm_writev(ff_pid, local, 1, remote, 1, 0) == sizeof(T));
}

template<typename T> T read_mem(uintptr_t addr) {
    T data = T{};
    if (ff_pid <= 0 || addr < 0x10000) return data;
    struct iovec local[1]; struct iovec remote[1];
    local[0].iov_base = &data; local[0].iov_len = sizeof(T);
    remote[0].iov_base = (void*)addr; remote[0].iov_len = sizeof(T);
    if (process_vm_readv(ff_pid, local, 1, remote, 1, 0) != sizeof(T)) return T{};
    return data;
}

Vector2 CalcAngle(Vector3 src, Vector3 dst) {
    Vector3 delta = { dst.x - src.x, dst.y - src.y, dst.z - src.z };
    float hyp = sqrt(delta.x * delta.x + delta.z * delta.z);
    Vector2 angles;
    angles.x = atan2(delta.z, delta.x) * 57.29577951308232f;
    angles.y = -atan2(delta.y, hyp) * 57.29577951308232f;
    return angles;
}

void* hack_thread(void* arg) {
    sleep(5);
    system("su -c 'pidof com.dts.freefireth' > /data/local/tmp/ff_pid.txt");
    FILE* fp = fopen("/data/local/tmp/ff_pid.txt", "r");
    if (fp) { fscanf(fp, "%d", &ff_pid); fclose(fp); }
    
    if (ff_pid <= 0) return nullptr;

    char map_path[64]; snprintf(map_path, sizeof(map_path), "/proc/%d/maps", ff_pid);
    fp = fopen(map_path, "r");
    if (fp) {
        char line[512];
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "libil2cpp.so")) { il2cpp_base = strtoul(line, NULL, 16); break; }
        }
        fclose(fp);
    }
    if (il2cpp_base == 0) return nullptr;

    JNIEnv* env; g_vm->AttachCurrentThread(&env, nullptr);

    while (true) {
        if (aimbot_on || esp_on) {
            uintptr_t init_base = read_mem<uintptr_t>(il2cpp_base + OFFSET_INIT_BASE);
            if (init_base) {
                uintptr_t current_match = read_mem<uintptr_t>(init_base + OFFSET_CURRENT_MATCH);
                if (current_match) {
                    uintptr_t game = read_mem<uintptr_t>(current_match + OFFSET_GAME);
                    if (game) {
                        uintptr_t local_player = read_mem<uintptr_t>(game + OFFSET_LOCAL_PLAYER);
                        uintptr_t entities_dict = read_mem<uintptr_t>(game + OFFSET_DICT_ENTITIES);

                        if (local_player && entities_dict) {
                            uintptr_t camera_t = read_mem<uintptr_t>(local_player + OFFSET_MAIN_CAMERA_TRANSFORM);
                            Vector3 camera_pos = read_mem<Vector3>(camera_t + 0x20);
                            float matrix[16];
                            for(int i=0; i<16; i++) matrix[i] = read_mem<float>(camera_t + OFFSET_VIEW_MATRIX + (i*4));

                            int w = 1080; int h = 2400;
                            uintptr_t entries = read_mem<uintptr_t>(entities_dict + 0x18);
                            int count = read_mem<int>(entities_dict + 0x20);

                            Vector3 best_target = {0, 0, 0};
                            float closest_dist = aim_fov;

                            if (entries && count > 0 && count < 100) {
                                jclass listClass = env->FindClass("java/util/ArrayList");
                                jmethodID listInit = env->GetMethodID(listClass, "<init>", "()V");
                                jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
                                jobject list = env->NewObject(listClass, listInit);

                                for (int i = 0; i < count; i++) {
                                    uintptr_t entry = entries + 0x20 + (i * 0x18);
                                    uintptr_t enemy = read_mem<uintptr_t>(entry + 0x10);
                                    
                                    if (enemy && enemy != local_player) {
                                        bool is_dead = read_mem<bool>(enemy + OFFSET_PLAYER_IS_DEAD);
                                        if (!is_dead) {
                                            uintptr_t head_t = read_mem<uintptr_t>(enemy + OFFSET_HEAD);
                                            uintptr_t chest_t = read_mem<uintptr_t>(enemy + OFFSET_CHEST);
                                            uintptr_t root_t = read_mem<uintptr_t>(enemy + OFFSET_ROOT);

                                            if (head_t && chest_t && root_t) {
                                                Vector3 h_pos = read_mem<Vector3>(head_t + 0x20);
                                                Vector3 c_pos = read_mem<Vector3>(chest_t + 0x20);
                                                Vector3 r_pos = read_mem<Vector3>(root_t + 0x20);

                                                Vector2 sh, sc, sr;
                                                if (WorldToScreen(matrix, h_pos, w, h, &sh) &&
                                                    WorldToScreen(matrix, c_pos, w, h, &sc) &&
                                                    WorldToScreen(matrix, r_pos, w, h, &sr)) {
                                                    
                                                    if (esp_on) {
                                                        float bh = sr.y - sh.y; float bw = bh * 0.45f;
                                                        jfloatArray seg = env->NewFloatArray(12);
                                                        jfloat s[12] = { sh.x, sh.y, sc.x, sc.y, sc.x, sc.y, sr.x, sr.y, sh.x, sh.y, sr.x, sr.y };
                                                        env->SetFloatArrayRegion(seg, 0, 12, s);
                                                        jobject entity = env->NewObject(g_entityClass, g_entityInit, sh.x - bw/2, sh.y, bw, bh, 0xFFFF0000, seg, 0xFFFFFFFF);
                                                        env->CallBooleanMethod(list, listAdd, entity);
                                                    }

                                                    float dist = sqrt(pow(sh.x - w/2, 2) + pow(sh.y - h/2, 2));
                                                    if (dist < closest_dist && dist > 0.1f) { 
                                                        closest_dist = dist; 
                                                        best_target = (target_bone == 0) ? h_pos : c_pos; 
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                env->CallVoidMethod(g_overlayService, g_updateEntities, list);
                            }

                            if (best_target.x != 0 && best_target.y != 0 && aimbot_on) {
                                Vector2 aim_angles = CalcAngle(camera_pos, best_target);
                                uintptr_t aim_addr = local_player + OFFSET_AIM_ROTATION;
                                Vector2 current_angles = read_mem<Vector2>(aim_addr);
                                Vector2 smoothed;
                                smoothed.x = current_angles.x + (aim_angles.x - current_angles.x) / aim_smooth;
                                smoothed.y = current_angles.y + (aim_angles.y - current_angles.y) / aim_smooth;
                                write_mem<float>(aim_addr, smoothed.x);
                                write_mem<float>(aim_addr + 0x4, smoothed.y);
                            }
                        }
                    }
                }
            }
        }
        usleep(10000);
    }
    return nullptr;
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) { g_vm = vm; return JNI_VERSION_1_6; }

extern "C" JNIEXPORT void JNICALL Java_com_example_rootpanel_OverlayService_nativeStart(JNIEnv* env, jobject thiz) {
    g_overlayService = env->NewGlobalRef(thiz);
    jclass serviceClass = env->GetObjectClass(thiz);
    g_updateEntities = env->GetMethodID(serviceClass, "updateEntities", "(Ljava/util/List;)V");
    g_entityClass = env->FindClass("com/example/rootpanel/Entity");
    g_entityInit = env->GetMethodID(g_entityClass, "<init>", "(FFFFI[FI)V");
    pthread_t pt; pthread_create(&pt, NULL, hack_thread, NULL); pthread_detach(pt);
}

extern "C" JNIEXPORT void JNICALL Java_com_example_rootpanel_OverlayService_setAimbot(JNIEnv* env, jobject thiz, jboolean state) { aimbot_on = state; }
extern "C" JNIEXPORT void JNICALL Java_com_example_rootpanel_OverlayService_setEsp(JNIEnv* env, jobject thiz, jboolean state) { esp_on = state; }
extern "C" JNIEXPORT void JNICALL Java_com_example_rootpanel_OverlayService_setFov(JNIEnv* env, jobject thiz, jint val) { aim_fov = val; }
extern "C" JNIEXPORT void JNICALL Java_com_example_rootpanel_OverlayService_setSmooth(JNIEnv* env, jobject thiz, jint val) { aim_smooth = val == 0 ? 1.0f : (float)val; }
extern "C" JNIEXPORT void JNICALL Java_com_example_rootpanel_OverlayService_setBone(JNIEnv* env, jobject thiz, jint val) { target_bone = val; }

extern "C" JNIEXPORT void JNICALL Java_com_example_rootpanel_OverlayView_nativeDraw(JNIEnv* env, jobject, jobject canvas, jobject paint, jfloatArray jBoxes, jintArray jBoxColors, jfloatArray jLines, jintArray jLineColors) {
    jclass canvasCls = env->GetObjectClass(canvas); jclass paintCls = env->GetObjectClass(paint);
    jmethodID drawRect = env->GetMethodID(canvasCls, "drawRect", "(FFFFLandroid/graphics/Paint;)V");
    jmethodID drawLine = env->GetMethodID(canvasCls, "drawLine", "(FFFFLandroid/graphics/Paint;)V");
    jmethodID setColor = env->GetMethodID(paintCls, "setColor", "(I)V");
    jsize nBoxes = env->GetArrayLength(jBoxColors); jsize nLines = env->GetArrayLength(jLineColors);
    jfloat* boxes = env->GetFloatArrayElements(jBoxes, nullptr); jint* boxColors = env->GetIntArrayElements(jBoxColors, nullptr);
    jfloat* lines = env->GetFloatArrayElements(jLines, nullptr); jint* lineColors = env->GetIntArrayElements(jLineColors, nullptr);
    for (jsize i = 0; i < nBoxes; i++) {
        env->CallVoidMethod(paint, setColor, boxColors[i]);
        env->CallVoidMethod(canvas, drawRect, boxes[i*4], boxes[i*4+1], boxes[i*4]+boxes[i*4+2], boxes[i*4+1]+boxes[i*4+3], paint);
    }
    for (jsize i = 0; i < nLines; i++) {
        env->CallVoidMethod(paint, setColor, lineColors[i]);
        env->CallVoidMethod(canvas, drawLine, lines[i*4], lines[i*4+1], lines[i*4+2], lines[i*4+3], paint);
    }
    env->ReleaseFloatArrayElements(jBoxes, boxes, JNI_ABORT); env->ReleaseIntArrayElements(jBoxColors, boxColors, JNI_ABORT);
    env->ReleaseFloatArrayElements(jLines, lines, JNI_ABORT); env->ReleaseIntArrayElements(jLineColors, lineColors, JNI_ABORT);
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_com_example_rootpanel_OverlayView_nativeWorldToScreen(JNIEnv* env, jclass, jfloatArray jm, jfloat x, jfloat y, jfloat z, jint width, jint height) {
    if (env->GetArrayLength(jm) < 16) return nullptr;
    jfloat m[16]; env->GetFloatArrayRegion(jm, 0, 16, m);
    Vector3 p = { x, y, z }; Vector2 s;
    if (!WorldToScreen(m, p, width, height, &s)) return nullptr;
    jfloatArray out = env->NewFloatArray(2); jfloat v[2] = { s.x, s.y };
    env->SetFloatArrayRegion(out, 0, 2, v); return out;
}
