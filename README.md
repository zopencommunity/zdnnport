[![Automatic version updates](https://github.com/zopencommunity/zDNNport/actions/workflows/bump.yml/badge.svg)](https://github.com/zopencommunity/zDNNport/actions/workflows/bump.yml)

# zDNN

IBM z Deep Neural Network (zDNN) Library — provides a C API for the Neural Network Processing Assist (NNPA) facility of the IBM Integrated Accelerator for AI, available on IBM z16 / LinuxONE 4 (Telum I) and later.

zDNN accelerates neural network operations (matrix multiply, activation functions, data format conversion) using the NNPA co-processor, which is particularly effective for prefill-dominated workloads such as embedding generation and LLM inference.

This port enables zDNN to build and run on z/OS using the IBM Open Enterprise SDK for C/C++ (clang). The upstream project targets Linux on Z with GCC and IBM XL C; this port adds z/OS clang support.

> **Hardware requirement:** NNPA acceleration requires the Integrated Accelerator for AI,
> introduced with IBM z16 / LinuxONE 4 (Telum I). IBM z17 / LinuxONE 5 (Telum II) adds NNPA
> parameter block format 1 and INT8 support, which zDNN 1.1.x exposes on top of the Telum I
> function set. On z15 and earlier there is no NNPA facility: `zdnn_is_nnpa_installed()`
> returns 0 and NNPA operations are unavailable.
>
> The library itself builds at a **z15 baseline** (`ZDNN_ARCH` in `config.zdnn`) and
> detects NNPA at run time via STFLE bit 165, so one binary covers both: on z16 and
> later it uses the accelerator, on z15 and earlier `zdnn_is_nnpa_installed()` returns
> 0, `zdnn_init()` skips NNPA-QAF, `invoke_nnpa()` returns `ZDNN_UNAVAILABLE_FUNCTION`,
> and the caller falls back. Only `convert_hw.c` -- which spells out the arch14
> conversion mnemonics and whose routines are unreachable without NNPA -- is
> assembled for z16, via `ZDNN_NNPA_ARCH` (see `zdnn/Makefile`).

# Installation and Usage

Use the zopen package manager ([QuickStart Guide](https://zopen.community/#/Guides/QuickStart)) to install:
```bash
zopen install zdnn
```
# Building from Source
Clone the repository:
```bash 
git clone https://github.com/zopencommunity/zDNNport.git
cd zDNNport
```
Build using zopen:
```bash
zopen build -vv
```

See the zopen porting guide for more details.

# z/OS Clang Port Notes
The upstream zDNN library was written for Linux on Z (GCC) and z/OS (IBM XL C).
This port adds support for building with clang on z/OS. Key changes are documented
in ```patches/README.md``` and include:

- New *-OS/390 clang case in ```config.zdnn``` (z/OS ```uname -m ```returns machine model, not s390x)
- Clang compatibility macros for XL C extensions (```_Packed```, ```__vector```, ```vec_float```)
- HLASM mnemonic inline asm replacing DC XL6'...' and GNU .insn syntax
- STFLE-based NNPA detection replacing XL C CVT walk for clang
- Guards for XL C-only system headers (```cvt.h```, ```ihaecvt.h```) and intrinsics (```__dcbt```, ```ctrace```)
  
Build dependencies: ```make```, ```autoconf```, ```automake```, ```coreutils```

Build flags required: ```-fzvector``` ```-mzvector``` ```-march=z16``` ```-D_POSIX_C_SOURCE=200809L```

# Documentation
[Upstream zDNN documentation](https://github.com/ibm/zdnn) 

[NNPA backend for llama.cpp](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/zDNN.md)

# Troubleshooting 
```zdnn_is_nnpa_installed()``` returns ```0```  
&rarr; The machine does not have NNPA hardware (STFLE facility bit 165). Requires IBM z16 / LinuxONE 4 or later.

Build fails with ```cannot use 'float' with '__vector'```  
&rarr; Ensure ```-fzvector``` ```-mzvector``` ```-march=z16``` are in Z```OPEN_EXTRA_CFLAGS```.

autoreconf not found during bootstrap  
&rarr; Ensure ```autoconf``` and ```automake``` are in ```ZOPEN_STABLE_DEPS``` and installed via zopen.

# Contributing
Contributions are welcome! Please follow the zopen contribution guidelines.

