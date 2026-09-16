# GameSpace sepolicy

BOARD_VENDOR_SEPOLICY_DIRS += \
    packages/apps/GameSpace/sepolicy/vendor

PRODUCT_PRIVATE_SEPOLICY_DIRS += \
    packages/apps/GameSpace/sepolicy/private

PRODUCT_PUBLIC_SEPOLICY_DIRS += \
    packages/apps/GameSpace/sepolicy/public

# Platform-specific sepolicy
ifneq ($(filter mt%,$(TARGET_BOARD_PLATFORM)),)
    # MediaTek
    BOARD_VENDOR_SEPOLICY_DIRS += \
        packages/apps/GameSpace/sepolicy/vendor_mtk
else
    # Additional rules for kernels newer than 5.10
    # or next-gen Qualcomm platforms
    ifeq ($(filter 4.19 5.4 5.10,$(TARGET_KERNEL_VERSION)),)
        BOARD_VENDOR_SEPOLICY_DIRS += \
            packages/apps/GameSpace/sepolicy/vendor_qcom
    else ifneq ($(filter pineapple sun,$(TARGET_BOARD_PLATFORM)),)
        BOARD_VENDOR_SEPOLICY_DIRS += \
            packages/apps/GameSpace/sepolicy/vendor_qcom_6x
    endif
endif