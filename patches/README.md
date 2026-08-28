# Patches

Upstream zDNN targets Linux on Z (GCC) and z/OS (IBM XL C). These patches add a
third configuration: z/OS with the IBM Open Enterprise SDK for C/C++ (clang).

Every clang-specific path is guarded with `defined(__MVS__) && defined(__clang__)`
so the existing GCC and XL C builds are untouched.

## Build configuration

**config.zdnn** — new `*-OS/390` clang target
- Adds a clang case (z/OS `uname -m` reports the machine model, not `s390x`); the
  original XL C case is kept as `DISABLED-xlc-OS/390`.
- One shared `ZDNN_COMMON_CFLAGS` feeds both `CFLAGS` and `CFLAGS_INIT`. The
  Makefile applies `CFLAGS_INIT` to `zdnn_init.c` only, so letting the two
  diverge silently builds that one file differently from the rest of the library.
- `ZDNN_ARCH` (default `z15`) is the baseline for the whole library. Keep it at or
  below the consuming application's `-march` so libzdnn stays loadable on pre-NNPA
  hardware; NNPA is detected at run time, not at build time.
- The zoslib include paths come from `ZOSLIB_HOME`, exported by the zoslib zopen
  dependency. Never hardcode them.
- Enum width is ABI. `zdnn_tensor_desc` leads with three enum fields, so every
  translation unit here and every consumer of `zdnn.h` must agree. This uses the
  ibm-clang default (short enums, matching the XL C ABI); forcing
  `-fno-short-enums` moves `dim1` from offset 16 to 24 and changes `sizeof` from
  20 to 28.

**zdnn/Makefile** — per-file architecture level
- `convert_hw.c` gets its own rule so `ZDNN_NNPA_ARCH_FLAGS` can raise the
  assembler architecture for that file alone. It is the only file spelling out
  arch14 NNPA mnemonics, and every routine in it is unreachable without the
  facility, so the rest of the library stays at the baseline.
- The variable is empty by default, so a plain Linux `make` issues exactly the
  same compile line as before.

## NNPA detection and invocation

**zdnn/zdnn_init.c**
- Guards the XL C system headers (`cvt.h`, `ihaecvt.h`, `ihafacl.h`, `ihapsa.h`).
- Adds a clang `zdnn_is_nnpa_installed()` that uses STFLE (facility bit 165)
  instead of walking the CVT, plus a clang `invoke_stfle()` in HLASM mnemonics.
- The STFLE operand buffer is explicitly 8-byte aligned: the instruction requires
  a doubleword boundary and a `char` array carries no such guarantee.
- Marks `zdnn_init()` `__attribute__((constructor))`. `initializer.cpp` only runs
  for a shared library; a static libzdnn.a needs the constructor to be reached
  through `zdnn_init.o`, which callers already pull in.

**zdnn/zdnn.c**
- Guards the XL C CVT walk and the `#pragma`-based paths.
- `invoke_nnpa()` uses the raw `DC X'B93B0000'` encoding, so the NNPA instruction
  itself needs no assembler architecture bump.
- The facility check is cached. `invoke_nnpa()` is the per-operation hot path and
  STFLE is serializing; z/OS cannot change the facility list under a running
  program, which is why the XL C path reads the static system copy.

**zdnn/query.c** — guards the XL C-only system headers.

## Vector conversion

**zdnn/convert_hw.c**
- Adds clang asm paths using HLASM mnemonics (VCRNF, VCNF, VCLFNH, VCLFNL, VCFN)
  in place of the GNU `.insn vrr` forms.
- `dlflt_min_vec` / `dlflt_max_vec` / `dlflt_inf_vec` stay `vector float`.
  `saturate_fp32_to_dlf16()` compares against them with `vec_min`/`vec_max`, which
  are type-driven: as `vec_float32` (`vector unsigned int`) the clamp becomes an
  unsigned integer clamp of the bit patterns, `0xCFFF8000` sorts above
  `0x4FFF8000`, and every finite input collapses to a constant.
- `vec_load_len` is called with `const unsigned int *` so it returns the integer
  vector type directly rather than relying on a reinterpret.

**zdnn/zdnn_private.h**
- Maps `_Packed` (XL C only) to nothing and `vector` to `__vector` for clang.
- Selects `<vecintrin.h>` for clang, `<builtins.h>` for XL C, `<s390intrin.h>` for
  Linux on Z.
- Skips the `.insn`-based `vec_float`/`vec_round` fallback macros for clang, which
  gets them from `vecintrin.h`; `vec_float` maps to `__builtin_convertvector`.
- Fixes a header guard mismatch (`ZDNN_ZDNN_PRIVATE_H` -> `ZDNN_ZDNN_PRIVATE_H_`).

## Diagnostics

**zdnn/stickify.c**
- Falls back to `__builtin_prefetch` for the `__dcbt` / `__dcbtst` / `__dcbf`
  cache intrinsics, which are XL C only.
- Compiles out the `FE_INEXACT` branch of `handle_fp_errors()` and
  `handle_fp_errors_saturation()` for z/OS clang.
  **Known issue:** this is a blunt workaround, not a fix. FP32 to DLFLOAT16 is
  inherently inexact, and the flag appears to survive differently under z/OS LE
  than under Linux. The correct fix is to save and restore the FP exception
  environment around the conversion (`feholdexcept` / `fesetenv`) rather than
  dropping the check, which also loses genuine live-migration detection.

**zdnn/status.c** — guards the XL C `CEE3DMP` dump path and the `backtrace()` path,
and prints a placeholder for z/OS clang, which has neither.
