// Declaração equivalente à da fabricante (GWM/Beantechs/Autolink), central BUX1, Android 9.
// Fonte: ClusterService (com.autolink.clusterservice 1.0, versionCode 1), system/app/ClusterService/oat/arm64/ClusterService.vdex.
// Ordem dos métodos = número da transação (comentário no fim de cada linha), conferido no dex.
// Nomes de parâmetro escolhidos aqui (o dex da fabricante não os guarda).
package com.autolink.clusterservice;

import com.autolink.cluster.ClusterMsgData;
import com.autolink.clusterservice.IClusterCallback;

interface IClusterService {
    void registerCallback(IClusterCallback callback); // 1
    void unregisterCallback(IClusterCallback callback); // 2
    void setMsg(int arg0, in ClusterMsgData data); // 3
    void getMsg(int arg0); // 4
    ClusterMsgData getMsgData(int arg0); // 5
}
