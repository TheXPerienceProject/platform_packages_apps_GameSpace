# GameSpace sepolicy

$(warning GameSpace sepolicy)

BOARD_VENDOR_SEPOLICY_DIRS += \
     packages/apps/GameSpace/sepolicy/vendor
PRODUCT_PRIVATE_SEPOLICY_DIRS += \
     packages/apps/GameSpace/sepolicy/private
PRODUCT_PUBLIC_SEPOLICY_DIRS += \
     packages/apps/GameSpace/sepolicy/public

# Add KGSL only on qcom
ifeq ($(filter mt%,$(TARGET_BOARD_PLATFORM)),)
BOARD_VENDOR_SEPOLICY_DIRS += \
     packages/apps/GameSpace/sepolicy/vendor
endif
