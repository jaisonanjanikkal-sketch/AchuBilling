export async function shareInvoiceAsImage(txn, biz, showToast) {
  try {
    const width = 600;
    const padding = 30;
    const rowHeight = 40;
    const headerHeight = 240;
    const itemsCount = txn.items.length;
    const footerHeight = 200;
    const height = headerHeight + (itemsCount * rowHeight) + footerHeight;

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');

    // Fill white background
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, width, height);

    // Business Name
    ctx.fillStyle = '#1d4ed8'; // Primary blue
    ctx.font = 'bold 32px system-ui, -apple-system, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(biz.name || 'My Business', width / 2, 70);

    // Business details
    ctx.fillStyle = '#475569'; // slate-600
    ctx.font = '20px system-ui, -apple-system, sans-serif';
    let detailY = 105;
    const details = [];
    if (biz.phone) details.push(`Tel: ${biz.phone}`);
    if (biz.address) details.push(biz.address);
    if (details.length > 0) {
      ctx.fillText(details.join(' · '), width / 2, detailY);
      detailY += 30;
    }

    // Divider
    ctx.strokeStyle = '#cbd5e1';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(padding, detailY);
    ctx.lineTo(width - padding, detailY);
    ctx.stroke();

    detailY += 40;

    // Invoice Title
    ctx.fillStyle = '#1d4ed8';
    ctx.font = 'bold 24px system-ui, -apple-system, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('TAX INVOICE', padding, detailY);

    // Invoice Meta (right side)
    ctx.fillStyle = '#0f172a';
    ctx.font = 'bold 20px system-ui, -apple-system, sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText(`Invoice #: ${txn.id}`, width - padding, detailY);

    detailY += 30;
    ctx.fillStyle = '#475569';
    ctx.font = '18px system-ui, -apple-system, sans-serif';
    const dateStr = new Date(txn.date).toLocaleDateString('en-IN', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
    ctx.fillText(`Date: ${dateStr}`, width - padding, detailY);

    ctx.textAlign = 'left';
    ctx.fillText('Payment Mode: Cash', padding, detailY);

    detailY += 30;

    // Table Header Divider
    ctx.strokeStyle = '#cbd5e1';
    ctx.beginPath();
    ctx.moveTo(padding, detailY);
    ctx.lineTo(width - padding, detailY);
    ctx.stroke();

    detailY += 25;

    // Table Headers
    ctx.fillStyle = '#0f172a';
    ctx.font = 'bold 18px system-ui, -apple-system, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('Item Name', padding, detailY);
    ctx.textAlign = 'right';
    ctx.fillText('Qty', 380, detailY);
    ctx.fillText('Rate', 480, detailY);
    ctx.fillText('Amount', width - padding, detailY);

    detailY += 15;
    ctx.strokeStyle = '#94a3b8';
    ctx.beginPath();
    ctx.moveTo(padding, detailY);
    ctx.lineTo(width - padding, detailY);
    ctx.stroke();

    detailY += 30;

    // Table Rows
    ctx.fillStyle = '#334155';
    ctx.font = '18px system-ui, -apple-system, sans-serif';
    for (const item of txn.items) {
      ctx.textAlign = 'left';
      // Truncate name if too long
      let displayName = item.itemName;
      if (displayName.length > 25) displayName = displayName.slice(0, 22) + '...';
      ctx.fillText(displayName, padding, detailY);

      ctx.textAlign = 'right';
      const qtyStr = Number(Number(item.quantity).toFixed(3)).toString();
      ctx.fillText(qtyStr, 380, detailY);
      ctx.fillText('₹' + Number(item.rate).toFixed(2), 480, detailY);
      ctx.fillText('₹' + Number(item.amount).toFixed(2), width - padding, detailY);
      detailY += rowHeight;
    }

    detailY -= 15;
    ctx.strokeStyle = '#cbd5e1';
    ctx.beginPath();
    ctx.moveTo(padding, detailY);
    ctx.lineTo(width - padding, detailY);
    ctx.stroke();

    detailY += 35;

    // Grand Total
    ctx.fillStyle = '#16a34a'; // green-600
    ctx.font = 'bold 24px system-ui, -apple-system, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('TOTAL DUE', padding, detailY);

    ctx.textAlign = 'right';
    ctx.fillText('₹' + Number(txn.total).toLocaleString('en-IN', { minimumFractionDigits: 2 }), width - padding, detailY);

    detailY += 50;
    ctx.strokeStyle = '#cbd5e1';
    ctx.beginPath();
    ctx.moveTo(padding, detailY);
    ctx.lineTo(width - padding, detailY);
    ctx.stroke();

    detailY += 40;

    // Footer
    ctx.fillStyle = '#64748b';
    ctx.font = 'italic 18px system-ui, -apple-system, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('Thank you for your business!', width / 2, detailY);

    // Convert to blob and share
    canvas.toBlob(async (blob) => {
      if (!blob) {
        if (showToast) showToast('Failed to generate invoice image', 'error');
        return;
      }
      
      const file = new File([blob], `invoice_${txn.id}.png`, { type: 'image/png' });

      if (navigator.share && navigator.canShare && navigator.canShare({ files: [file] })) {
        try {
          await navigator.share({
            files: [file],
            title: `Invoice #${txn.id}`,
            text: `Here is the invoice #${txn.id} from ${biz.name || 'My Business'}`
          });
        } catch (err) {
          if (err.name !== 'AbortError') {
            console.error('Error sharing file:', err);
            if (showToast) showToast('Error sharing image', 'error');
          }
        }
      } else {
        // Fallback: download the image
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `invoice_${txn.id}.png`;
        a.click();
        URL.revokeObjectURL(url);
        if (showToast) showToast('Image downloaded! Share it from your files.', 'info');
      }
    }, 'image/png');

  } catch (err) {
    console.error('shareInvoiceAsImage error:', err);
    if (showToast) showToast('Failed to generate image', 'error');
  }
}
