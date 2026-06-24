.class public Lcom/lastasylum/alliance/game/MapFlyBridgeInit;
.super Ljava/lang/Object;
.source "MapFlyBridgeInit.java"


# static fields
.field private static volatile started:Z


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static ensureStarted(Landroid/content/Context;)V
    .locals 3

    const-string v0, "MapFlyBridgeInit"

    const-string v1, "ensureStarted"

    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    sget-boolean v0, Lcom/lastasylum/alliance/game/MapFlyBridgeInit;->started:Z

    if-nez v0, :cond_return

    const-string v0, "frida-gadget"

    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V

    const-string v0, "MapFlyBridgeInit"

    const-string v1, "frida-gadget loaded"

    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    const/4 v0, 0x1

    sput-boolean v0, Lcom/lastasylum/alliance/game/MapFlyBridgeInit;->started:Z

    new-instance v0, Lcom/lastasylum/alliance/game/MapFlyReceiver;

    invoke-direct {v0}, Lcom/lastasylum/alliance/game/MapFlyReceiver;-><init>()V

    new-instance v1, Landroid/content/IntentFilter;

    const-string v2, "com.lastasylum.alliance.action.MAP_FLY"

    invoke-direct {v1, v2}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    const/4 v2, 0x2

    invoke-virtual {p0, v0, v1, v2}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;I)Landroid/content/Intent;

    const-string v0, "MapFlyBridgeInit"

    const-string v1, "receiver registered"

    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    :cond_return
    return-void
.end method
