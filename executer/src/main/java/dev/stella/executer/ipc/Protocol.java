package dev.stella.executer.ipc;

/**
 * Shared-memory IPC protocol constants.
 * Must be kept in sync with cilent/src/protocol.h (see docs/protocol.md).
 *
 * Java = thin driver: rendering primitives + low-level actions + world queries.
 * C++  = brain: all game logic, modules, AI, pathfinding etc.
 */
public final class Protocol {
    public static final int MAGIC = 0x53544C31; // "STL1"
    public static final int VERSION = 2;

    public static final int HEADER_SIZE = 64;
    public static final int DATA_CAP = 256 * 1024; // power of two
    public static final int RING_META = 16;
    public static final long REGION_SIZE = RING_META + DATA_CAP;
    public static final long FILE_SIZE = HEADER_SIZE + 2L * REGION_SIZE;

    // header offsets
    public static final int H_MAGIC = 0;
    public static final int H_VERSION = 4;
    public static final int H_DATA_CAP = 8;

    // region base offsets
    public static final long OFF_NATIVE_TO_JAVA = HEADER_SIZE;
    public static final long OFF_JAVA_TO_NATIVE = HEADER_SIZE + REGION_SIZE;

    // ring meta
    public static final int RING_HEAD = 0;
    public static final int RING_TAIL = 4;
    public static final int RING_DATA = 16;

    // ============================================================
    // C++ → Java  (commands)
    // ============================================================

    // --- handshake ---
    public static final int OP_CONNECT       = 0;

    // --- rendering (C++ pushes draw calls, Java executes next frame) ---
    public static final int CMD_DRAW_LINE    = 50; // [x1f32][y1f32][z1f32][x2f32][y2f32][z2f32][coloru32]
    public static final int CMD_DRAW_BOX     = 51; // [xf32][yf32][zf32][wf32][hf32][df32][coloru32]
    public static final int CMD_DRAW_TEXT    = 52; // [xf32][yf32][utf8text][coloru32]
    public static final int CMD_DRAW_RECT    = 53; // [xf32][yf32][wf32][hf32][coloru32]
    public static final int CMD_DRAW_RECT_F  = 54; // [xf32][yf32][wf32][hf32][coloru32] (filled)
    public static final int CMD_CLEAR_RENDER = 55; // empty — clear draw buffer

    // --- actions (low-level primitives) ---
    public static final int CMD_SWING_HAND   = 10; // [handu8] 0=main 1=off
    public static final int CMD_ATTACK       = 11; // [entityIdI32]
    public static final int CMD_PLACE_BLOCK  = 12; // [xBi32][yBi32][zBi32][faceu8] face:0-5
    public static final int CMD_BREAK_BLOCK  = 13; // [xBi32][yBi32][zBi32]
    public static final int CMD_SET_SLOT     = 14; // [slotu8] 0-8
    public static final int CMD_USE_ITEM     = 15; // empty
    public static final int CMD_SNEAK        = 16; // [stateu8] 0=stop 1=start
    public static final int CMD_SPRINT       = 17; // [stateu8]
    public static final int CMD_SET_VELOCITY = 18; // [xf32][yf32][zf32]
    public static final int CMD_SEND_CHAT    = 19; // [utf8text]
    public static final int CMD_LOOK_AT      = 20; // [xf64][yf64][zf64]
    public static final int CMD_MOVE_TO      = 21; // [xf64][yf64][zf64]
    public static final int CMD_SET_GAMMA    = 22; // [valueF32]
    public static final int CMD_AUTO_DAMAGE  = 23; // [entityIdI32] attack-entity-in-range loop
    public static final int CMD_STOP_ALL     = 24; // empty — cancel all ongoing actions

    // --- queries (C++ requests, Java responds with query result event) ---
    public static final int CMD_GET_PLAYER_POS  = 30; // empty
    public static final int CMD_GET_ENTITIES    = 31; // [rangeF32]
    public static final int CMD_GET_BLOCK_AT    = 32; // [xBi32][yBi32][zBi32]
    public static final int CMD_GET_RAYTRACE    = 33; // empty (from camera)
    public static final int CMD_GET_PLAYERS     = 34; // [rangeF32]
    public static final int CMD_GET_INVENTORY   = 35; // empty

    // --- module control (C++ sets module state) ---
    public static final int CMD_SET_MODULE      = 25; // [moduleIdU8][enabledU8]

    // ============================================================
    // Java → C++  (events)
    // ============================================================

    public static final int EV_CONNECTED      = 100;
    public static final int EV_PONG           = 101; // echo

    // --- game events ---
    public static final int EV_TICK           = 110; // [worldTimeI64]
    public static final int EV_RENDER_3D      = 111; // [camXf64][camYf64][camZf64][pitchf32][yawf32][partialf32]
    public static final int EV_RENDER_2D      = 112; // [screenWi32][screenHi32][partialf32]
    public static final int EV_ATTACK         = 113; // [entityIdI32]
    public static final int EV_DAMAGE         = 114; // [amountF32]
    public static final int EV_CHAT           = 115; // [utf8text]
    public static final int EV_BLOCK_UPDATE   = 116; // [xBi32][yBi32][zBi32][blockIdI32]
    public static final int EV_DEATH          = 117; // empty
    public static final int EV_MODULE_TOGGLE  = 118; // [moduleIdU8][enabledU8]
    public static final int EV_SETTINGS_UPDATE = 119; // [keyLenU8][keyUtf8][valueUtf8]

    // --- query responses ---
    public static final int EV_PLAYER_POS     = 130; // [xF64][yF64][zF64][pitchF32][yawF32][onGroundU8]
    public static final int EV_ENTITY_LIST    = 131; // [countI32] then per entity: [idI32][typeI32][xF32][yF32][zF32][healthF32][armorI32]
    public static final int EV_BLOCK_AT       = 132; // [blockIdI32][metaU8]
    public static final int EV_RAYTRACE       = 133; // [hitU8][xBi32][yBi32][zBi32][faceU8][entityIdI32]
    public static final int EV_PLAYER_LIST    = 134; // [countI32] then per player: [entityIdI32][nameUtf8][xF32][yF32][zF32]
    public static final int EV_INVENTORY      = 135; // [countI32] then per slot: [slotU8][itemIdI32][countU8]

    private Protocol() {}
}
