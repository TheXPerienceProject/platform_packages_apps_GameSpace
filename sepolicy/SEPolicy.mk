# GameSpace sepolicy

#$(warning GameSpace sepolicy)

BOARD_VENDOR_SEPOLICY_DIRS += \
     packages/apps/GameSpace/sepolicy/vendor
PRODUCT_PRIVATE_SEPOLICY_DIRS += \
     packages/apps/GameSpace/sepolicy/private
PRODUCT_PUBLIC_SEPOLICY_DIRS += \
     packages/apps/GameSpace/sepolicy/public

# Add KGSL only on qcom
ifeq ($(filter mt%,$(TARGET_BOARD_PLATFORM)),)
    # Include generic Qualcomm SEPolicy if not a MediaTek platform
    BOARD_VENDOR_SEPOLICY_DIRS += packages/apps/GameSpace/sepolicy/vendor_qcom

    # Include specific SEPolicy rules for kernels newer than 5.10 (e.g., 5.15, 6.1, 6.6)
    # or next-gen Qualcomm platforms (Pineapple / Sun SM8650/SM8750)
    ifeq ($(filter 4.19 5.4 5.10,$(TARGET_KERNEL_VERSION)),)
        BOARD_VENDOR_SEPOLICY_DIRS += packages/apps/GameSpace/sepolicy/vendor_qcom_6x
    else ifneq ($(filter pineapple sun,$(TARGET_BOARD_PLATFORM)),)
        BOARD_VENDOR_SEPOLICY_DIRS += packages/apps/GameSpace/sepolicy/vendor_qcom_6x
    endif
endif