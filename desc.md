# 工程背景

本工程是一个compose跨平台项目，支持Android、iOS、desktop，目的是复刻经典的爬虫工具Jsoup。
因为Jsoup是使用Java写的，因此无法在compose跨平台项目中使用。

# 工程目标

实现与Jsoup相同的能力，不依赖jvm，接口与Jsoup兼容。只把包名改为xyz.thewind.ksoup。

在当前工程中新建一个ksoup模块，迁移代码还是仿照重写由你来判断

# 参考文献

Jsoup官方地址：https://github.com/jhy/jsoup


# 实施计划

请你分别扮演一下角色

产品经理：制定详细的目标

资深的程序员：实现迁移

技术经理：审核资深程序员写的代码，保障代码质量

根据以上背景，帮我制定详细的实施计划，保存为PLAN.md,然后实施迁移