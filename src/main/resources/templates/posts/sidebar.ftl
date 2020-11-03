<#macro sidebar sidebarVO>
    <div class="card" style="margin-bottom: 1rem;">
        <div class="card-body">
            <h5 class="card-title" style="font-size: 18px;">关注任霏博客</h5>
            <h6 class="card-subtitle mb-2 text-muted">扫码关注「任霏博客」微信订阅号</h6>
            <div class="text-center">
                <img src="https://cdn.renfei.net/images/WechatQR.png" class="img-fluid"
                     style="max-width: 140px;margin: auto;"/>
            </div>
            <div style="font-size: 12px;" class="mt-2">
                微博：<a href="https://weibo.com/5214619228" target="_blank" rel="nofollow noopener">任霏博客网</a><br/>
                Twitter：<a href="https://twitter.com/renfeii" target="_blank" rel="nofollow noopener">@renfeii</a><br/>
                Facebook:<a href="https://www.facebook.com/renfeii" target="_blank" rel="nofollow noopener">任霏</a>
            </div>
        </div>
    </div>
    <#if sidebarVO??>
        <#if sidebarVO.postSidebars?? && (sidebarVO.postSidebars?size > 0)>
            <#list sidebarVO.postSidebars as postSidebar>
                <#if postSidebar.title == "标签云">
                    <div class="list-group mb-2">
                        <a class="list-group-item list-group-item-action active">
                            ${postSidebar.title!}
                        </a>
                        <#if postSidebar.link?? && (postSidebar.link?size > 0)>
                            <div class="row">
                                <div class="col-12">
                                    <div class="border p-2">
                                        <#list postSidebar.link as link>
                                            <a href="${link.href!}"
                                               class="badge badge-pill badge-secondary">${link.text!?html}</a>
                                        </#list>
                                    </div>
                                </div>
                            </div>
                        </#if>
                    </div>
                <#else>
                    <div class="list-group mb-2">
                        <a class="list-group-item list-group-item-action active">
                            ${postSidebar.title!}
                        </a>
                        <#if postSidebar.link?? && (postSidebar.link?size > 0)>
                            <#list postSidebar.link as link>
                                <a href="${link.href!}"
                                   class="list-group-item list-group-item-action">${link.text!?html}</a>
                            </#list>
                        </#if>
                    </div>
                </#if>
                <#if postSidebar_index ==0 || postSidebar_index == 1>
                    <div class="d-none d-sm-block mb-2">
                        <ins class="adsbygoogle" style="display:block" data-ad-client="ca-pub-8859756463807757"
                             data-ad-slot="4995060553" data-ad-format="auto" data-full-width-responsive="true"></ins>
                        <script>
                            (adsbygoogle = window.adsbygoogle || []).push({});
                        </script>
                    </div>
                </#if>
            </#list>
        </#if>
    </#if>
    <#nested>
</#macro>